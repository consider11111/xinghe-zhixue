const trainingEscape=value=>String(value??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let teacherTrainingClass=null, trainingLoadId=0;

async function learningRequest(path,method='GET',body) {
  const response=await fetch('/api/education'+path,{method,headers:{'X-Auth-Token':token,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});
  const data=await response.json();
  if(!response.ok)throw new Error(data.message||'暂时无法读取训练，请刷新重试');
  return data;
}

function trainingCards(items) {
  return items.map(t=>`<article class="library-card"><div><span class="subject-badge math">${trainingEscape(t.subject)}</span><em>${t.source==='teacher'?'教师安排':'今日随机训练'}</em></div><h3>${trainingEscape(t.title)}</h3><p>${trainingEscape(t.class_name||'')}</p><small>${t.question_count} 道题 · 约 ${t.minutes} 分钟 · ${Number(t.progress)===100?'已完成':'待完成'}</small><button data-training-id="${t.id}">查看训练 →</button></article>`).join('')||'<p class="training-empty">暂无今日训练。未分配班级时，请联系管理员设置所属班级。</p>';
}
function bindTrainingCards(host,items) {
  host.querySelectorAll('[data-training-id]').forEach(b=>b.onclick=()=>openTraining(items.find(t=>t.id==b.dataset.trainingId)));
}
async function refreshStudentTraining() {
  const host=document.querySelector('#training');
  if(!host)return;
  try {
    const items=await learningRequest('/training');
    if(!host.isConnected)return;
    host.innerHTML=trainingCards(items);bindTrainingCards(host,items);
    const subtitle=document.querySelector('.welcome span');
    if(subtitle)subtitle.textContent=`今天为你安排了 ${items.length} 项班级训练，完成后可以记录学习进度。`;
  } catch(e) { if(host.isConnected)host.textContent=e.message; }
}
async function renderStudentTraining() {
  document.querySelector('main').innerHTML='<section class="student-page-head"><div><span>今日班级训练</span><h1>训练中心</h1><p>每天随机生成，老师可按班级调整。刷新可查看最新安排。</p></div><button class="student-head-action" onclick="location.reload()">返回学习总览</button></section><div class="training-library" id="studentTrainingList">正在加载…</div>';
  document.querySelectorAll('nav a').forEach((a,i)=>a.classList.toggle('active',i===2));
  const host=document.querySelector('#studentTrainingList');
  try {const items=await learningRequest('/training');if(!host.isConnected)return;host.innerHTML=trainingCards(items);bindTrainingCards(host,items);}
  catch(e){if(host.isConnected)host.textContent=e.message;}
}
async function showTodayPlan() {
  const m=document.createElement('div');m.className='role-mask';
  m.innerHTML='<div class="report-modal"><button class="back-auth" data-close>× 关闭</button><h2>今日学习计划</h2><div data-plans>正在加载…</div></div>';
  document.body.append(m);m.querySelector('[data-close]').onclick=()=>m.remove();
  try {
    const items=await learningRequest('/training');if(!m.isConnected)return;
    const host=m.querySelector('[data-plans]');host.innerHTML=trainingCards(items);
    host.querySelectorAll('[data-training-id]').forEach(b=>b.onclick=()=>{m.remove();openTraining(items.find(t=>t.id==b.dataset.trainingId));});
  } catch(e){m.querySelector('[data-plans]').textContent=e.message;}
}
function openTraining(task) {
  const m=document.createElement('div');m.className='role-mask';
  m.innerHTML=`<div class="report-modal"><button class="back-auth" data-close>× 关闭</button><p>${trainingEscape(task.subject)} · ${task.question_count} 道题 · ${task.minutes} 分钟</p><h2>${trainingEscape(task.title)}</h2><div class="training-content">${trainingEscape(task.content)}</div><p data-status role="status">完成练习后，点击下方按钮记录完成状态。</p><button class="auth-primary" data-complete ${Number(task.progress)===100?'disabled':''}>${Number(task.progress)===100?'已完成':'我已完成练习'}</button></div>`;
  document.body.append(m);m.querySelector('[data-close]').onclick=()=>m.remove();
  m.querySelector('[data-complete]').onclick=async e=>{
    e.target.disabled=true;
    try {await learningRequest('/training/'+task.id+'/complete','POST');m.querySelector('[data-status]').textContent='已保存完成状态';e.target.textContent='已完成';await refreshStudentTraining();if(document.querySelector('#studentTrainingList'))await renderStudentTraining();}
    catch(error){m.querySelector('[data-status]').textContent=error.message;e.target.disabled=false;}
  };
}
async function renderTeacherTasks() {
  const requestId=++trainingLoadId;
  document.querySelector('main').innerHTML='<section class="teacher-page"><div class="teacher-page-head"><div><span>班级训练管理</span><h2>今日训练</h2><p>每天自动生成一组训练，你可以添加、修改或删除本班训练。</p></div></div><div id="teacherTrainingHost">正在加载…</div></section>';
  setTeacherActive('tasks');
  const host=document.querySelector('#teacherTrainingHost');
  try {
    const classes=await learningRequest('/teacher/classes');
    if(requestId!==trainingLoadId||!host.isConnected)return;
    if(!classes.length){host.textContent='尚未分配班级，请联系管理员设置班级归属。';return;}
    if(!classes.some(c=>c.id==teacherTrainingClass))teacherTrainingClass=classes[0].id;
    const items=await learningRequest('/teacher/classes/'+teacherTrainingClass+'/training');
    if(requestId!==trainingLoadId||!host.isConnected)return;
    host.innerHTML=`<div class="training-controls"><label>所属班级 <select id="trainingClass">${classes.map(c=>`<option value="${c.id}" ${c.id==teacherTrainingClass?'selected':''}>${trainingEscape(c.name)}</option>`).join('')}</select></label><button class="teacher-create" id="addTraining">＋ 添加训练</button><button id="reloadTraining">刷新</button></div><div class="training-library">${items.map(t=>`<article class="library-card"><small>${trainingEscape(t.subject)} · ${t.source==='random'?'随机生成':'教师安排'}</small><h3>${trainingEscape(t.title)}</h3><div class="training-content">${trainingEscape(t.content)}</div><p>${t.question_count} 道题 · ${t.minutes} 分钟</p><div class="training-controls"><button data-edit-training="${t.id}">修改</button><button data-delete-training="${t.id}">删除</button></div></article>`).join('')||'<p>今日暂无训练，可以手动添加。</p>'}</div><p id="trainingMessage" role="status"></p>`;
    host.querySelector('#trainingClass').onchange=e=>{teacherTrainingClass=Number(e.target.value);renderTeacherTasks();};
    host.querySelector('#addTraining').onclick=()=>showTaskForm();
    host.querySelector('#reloadTraining').onclick=()=>renderTeacherTasks();
    host.querySelectorAll('[data-edit-training]').forEach(b=>b.onclick=()=>showTaskForm(items.find(t=>t.id==b.dataset.editTraining)));
    host.querySelectorAll('[data-delete-training]').forEach(b=>b.onclick=async()=>{
      if(!confirm('删除这项训练后，本班学生将不再看到它。确定删除？'))return;
      b.disabled=true;
      try{await learningRequest('/teacher/classes/'+teacherTrainingClass+'/training/'+b.dataset.deleteTraining,'DELETE');await renderTeacherTasks();}
      catch(e){host.querySelector('#trainingMessage').textContent=e.message;b.disabled=false;}
    });
  }catch(e){if(host.isConnected)host.textContent=e.message;}
}
function showTaskForm(task) {
  const classId=teacherTrainingClass;
  const m=document.createElement('div');m.className='role-mask';
  m.innerHTML=`<form class="report-modal plan-form"><button class="back-auth" type="button" data-close>× 关闭</button><h2>${task?'修改':'添加'}班级训练</h2><label>学科<input name="subject" maxlength="30" required value="${trainingEscape(task?.subject||'数学')}"></label><label>训练标题<input name="title" maxlength="100" required value="${trainingEscape(task?.title)}"></label><label>训练内容<textarea name="content" rows="6" maxlength="5000" required>${trainingEscape(task?.content)}</textarea></label><label>题数<input name="count" type="number" min="1" max="100" required value="${task?.question_count||3}"></label><label>预计时长（分钟）<input name="minutes" type="number" min="1" max="180" required value="${task?.minutes||15}"></label><p data-error role="alert"></p><button class="auth-primary" type="submit">保存训练</button></form>`;
  document.body.append(m);m.querySelector('[data-close]').onclick=()=>m.remove();
  m.querySelector('form').onsubmit=async e=>{
    e.preventDefault();const button=m.querySelector('[type=submit]');button.disabled=true;
    const form=new FormData(e.target),body=Object.fromEntries(form);body.count=Number(body.count);body.minutes=Number(body.minutes);
    try {await learningRequest('/teacher/classes/'+classId+'/training'+(task?'/'+task.id:''),task?'PUT':'POST',body);m.remove();await renderTeacherTasks();}
    catch(error){m.querySelector('[data-error]').textContent=error.message;button.disabled=false;}
  };
}
