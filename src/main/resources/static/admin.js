(() => {
  const roles = {student:'学生', teacher:'教师', admin:'管理员'};
  const kinds = {class:'班级', subject:'学科', term:'学期'};
  const state = {view:'accounts', tab:'users', kind:'class', query:'', role:'', status:'', page:1, users:[], data:[], audit:[]};
  let requestId = 0;
  const escape = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const date = value => value ? new Date(value).toLocaleString('zh-CN', {hour12:false}) : '-';
  const enabled = value => value === true || value === 1;
  const badge = value => `<span class="admin-status ${enabled(value)?'is-on':'is-off'}">${enabled(value)?'启用':'停用'}</span>`;
  const roleBadge = value => `<span class="admin-role ${escape(value)}">${roles[value] || escape(value)}</span>`;
  const empty = (text, cols) => `<tr><td colspan="${cols}" class="admin-empty">${text}</td></tr>`;

  async function request(path, method='GET', body) {
    let response;
    try { response = await fetch('/api/admin'+path, {method, headers:{'X-Auth-Token':token,'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined}); }
    catch (e) { throw new Error('无法连接服务，请稍后重试'); }
    let result;
    try { result = await response.json(); } catch (e) { throw new Error('管理服务未就绪，请确认已启动新版服务'); }
    if (!response.ok) {
      if (response.status === 401) { localStorage.removeItem('xinghe_token'); location.reload(); }
      throw new Error(result.message || (response.status===403?'当前账号没有管理员权限':'请求失败，请稍后重试'));
    }
    return result;
  }

  function notify(text) {
    document.querySelector('.admin-toast')?.remove();
    const el = document.createElement('div'); el.className='admin-toast'; el.setAttribute('role','status'); el.textContent=text;
    document.body.append(el); setTimeout(()=>el.remove(),4000);
  }

  function dialog(title, fields, save, label='保存') {
    const el=document.createElement('dialog'); el.className='admin-dialog';
    el.innerHTML=`<form><div class="admin-dialog-head"><h2>${escape(title)}</h2><button type="button" class="admin-close" aria-label="关闭">×</button></div><div class="admin-fields">${fields}</div><p class="admin-form-error" role="alert"></p><div class="admin-dialog-footer"><button type="button" class="admin-cancel">取消</button><button type="submit" class="admin-primary">${label}</button></div></form>`;
    document.body.append(el); el.showModal();
    el.querySelector('.admin-close').onclick=()=>el.close(); el.querySelector('.admin-cancel').onclick=()=>el.close();
    el.addEventListener('close',()=>el.remove());
    el.querySelector('form').onsubmit=async event=>{
      event.preventDefault(); const button=el.querySelector('[type=submit]'); button.disabled=true;
      el.querySelector('.admin-form-error').textContent='';
      try { await save(new FormData(event.target)); el.close(); }
      catch(e) { el.querySelector('.admin-form-error').textContent=e.message; }
      finally { button.disabled=false; }
    };
  }

  function editUser(user) {
    const self=user && Number(user.id)===Number(currentUser.id);
    dialog(user?'编辑账号':'新增账号', `
      <label>账号<input name="username" maxlength="50" required value="${escape(user?.username)}" ${user?'disabled':''} autocomplete="off"></label>
      <label>姓名<input name="displayName" maxlength="50" required value="${escape(user?.display_name)}"></label>
      <label>身份<select name="role" ${self?'disabled':''}>${Object.entries(roles).map(([key,value])=>`<option value="${key}" ${key===(user?.role||'student')?'selected':''}>${value}</option>`).join('')}</select></label>
      ${!user?'<label>初始密码<input type="password" name="password" required minlength="6" maxlength="128" autocomplete="new-password"></label>':''}
      <label class="admin-checkbox"><input type="checkbox" name="enabled" ${!user||enabled(user.enabled)?'checked':''} ${self?'disabled':''}>启用账号</label>`, async form=>{
        const body={username:form.get('username'),displayName:form.get('displayName'),role:self?'admin':form.get('role'),enabled:self?true:form.has('enabled'),password:form.get('password')};
        await request('/users'+(user?'/'+user.id:''),user?'PUT':'POST',body);
        if(self) { currentUser.displayName=body.displayName; document.querySelector('#userMenu span').textContent=body.displayName; }
        notify(user?'账号权限已更新':'账号已创建'); await load();
      });
  }

  function deleteUser(user) {
    dialog('删除账号', `<p>确定删除 ${escape(user.username)}（${escape(user.display_name)}）？删除后该账号将不能登录。</p>`,async()=>{
      await request('/users/'+user.id,'DELETE');notify('账号已删除');await load();
    },'确认删除');
  }

  async function assignClasses(user) {
    try {
      const [classes,assigned]=await Promise.all([fetch('/api/auth/classes').then(r=>{if(!r.ok)throw new Error('无法读取班级');return r.json();}),request('/users/'+user.id+'/classes')]);
      dialog('设置班级归属',`<p>${escape(user.display_name)} · 学生最多1个班级，教师最多2个班级</p>${classes.map(c=>`<label class="admin-checkbox"><input type="checkbox" name="classIds" value="${c.id}" ${assigned.some(a=>a.id===c.id)?'checked':''}>${escape(c.name)}</label>`).join('')||'<p>请先在基础数据中添加班级。</p>'}`,async form=>{
        await request('/users/'+user.id+'/classes','PUT',{classIds:form.getAll('classIds').map(Number)});notify('班级归属已更新');
      });
    } catch(e){notify(e.message);}
  }

  function resetPassword(user) {
    dialog('重置密码', `<p class="admin-dialog-note">${escape(user.username)} · ${escape(user.display_name)}</p><label>新密码<input type="password" name="password" minlength="6" maxlength="128" required autocomplete="new-password"></label><label>确认密码<input type="password" name="confirm" minlength="6" maxlength="128" required autocomplete="new-password"></label>`, async form=>{
      if(form.get('password')!==form.get('confirm')) throw new Error('两次输入的密码不一致');
      await request('/users/'+user.id+'/password','PUT',{password:form.get('password')});
      if(Number(user.id)===Number(currentUser.id)){localStorage.removeItem('xinghe_token');location.reload();return;}
      notify('密码已重置，该账号需重新登录');
    },'确认重置');
  }

  function editData(row) {
    const kind=row?.kind||state.kind;
    dialog((row?'编辑':'新增')+kinds[kind], `<label>编码<input name="code" required maxlength="40" value="${escape(row?.code)}" placeholder="${{class:'CLASS-001',subject:'MATH',term:'2026-AUTUMN'}[kind]}"></label><label>名称<input name="name" required maxlength="80" value="${escape(row?.name)}" placeholder="${{class:'高一（1）班',subject:'数学',term:'2026 秋季学期'}[kind]}"></label><label>备注<textarea name="details" maxlength="255" rows="3">${escape(row?.details)}</textarea></label><label class="admin-checkbox"><input type="checkbox" name="enabled" ${!row||enabled(row.enabled)?'checked':''}>启用资料</label>`,async form=>{
      await request('/base-data'+(row?'/'+row.id:''),row?'PUT':'POST',{kind,code:form.get('code'),name:form.get('name'),details:form.get('details'),enabled:form.has('enabled')});
      notify('基础资料已保存');await load();
    });
  }

  function stats(items) {
    return `<div class="admin-metrics">${items.map(([label,value,note])=>`<div><span>${label}</span><strong>${escape(value)}</strong><small>${escape(note)}</small></div>`).join('')}</div>`;
  }

  function accounts() {
    const counts=key=>state.users.filter(u=>u.role===key).length;
    return (state.demoMode?'<p class="admin-policy">体验模式：仅提供三个初始账号，修改内容重启后恢复；连接数据库后可新增账号。</p>':'')+stats([['账号总数',state.users.length,'全平台账号'],['管理员',counts('admin'),'系统管理权限'],['教师',counts('teacher'),'教学工作台'],['学生',counts('student'),'个人学习空间']])+`
      <div class="admin-tabs" role="tablist"><button role="tab" aria-selected="${state.tab==='users'}" data-tab="users">账号管理</button><button role="tab" aria-selected="${state.tab==='roles'}" data-tab="roles">角色权限</button></div>
      ${state.tab==='roles'?permissions():`<div class="admin-toolbar"><input type="search" id="adminSearch" aria-label="搜索账号或姓名" placeholder="搜索账号或姓名" value="${escape(state.query)}"><select id="adminRole" aria-label="筛选身份"><option value="">全部身份</option>${Object.entries(roles).map(([key,name])=>`<option value="${key}" ${key===state.role?'selected':''}>${name}</option>`).join('')}</select><select id="adminStatus" aria-label="筛选状态"><option value="">全部状态</option><option value="1" ${state.status==='1'?'selected':''}>启用</option><option value="0" ${state.status==='0'?'selected':''}>停用</option></select><button class="admin-primary" id="newUser" ${state.demoMode?'disabled title="连接数据库后可新增账号"':''}>新增账号</button></div><div id="accountTable"></div>`}`;
  }

  function permissions() {
    return `<div class="admin-table-wrap"><table><thead><tr><th>功能范围</th><th>管理员</th><th>教师</th><th>学生</th></tr></thead><tbody>
      <tr><td>账号查询、创建与权限调整</td><td class="permission-yes">允许</td><td>无权限</td><td>无权限</td></tr>
      <tr><td>班级、学科与学期资料维护</td><td class="permission-yes">允许</td><td>无权限</td><td>无权限</td></tr>
      <tr><td>系统检查与管理操作日志</td><td class="permission-yes">允许</td><td>无权限</td><td>无权限</td></tr>
      <tr><td>默认工作台</td><td>管理后台</td><td>班级教学</td><td>个人学习</td></tr>
      </tbody></table></div><section class="admin-policy"><h2>账号安全规则</h2><dl><div><dt>管理员授权</dt><dd>由现有管理员创建或调整身份</dd></div><div><dt>当前账号保护</dt><dd>不可停用自己或移除自己的管理员身份</dd></div><div><dt>停用与密码重置</dt><dd>停用后无法登录；重置密码后原登录状态失效</dd></div></dl></section>`;
  }

  function accountRows() {
    const filtered=state.users.filter(u=>(!state.role||u.role===state.role)&&(!state.status||Number(enabled(u.enabled))===Number(state.status))&&`${u.username} ${u.display_name}`.toLowerCase().includes(state.query.toLowerCase()));
    const pages=Math.max(1,Math.ceil(filtered.length/8));state.page=Math.min(state.page,pages);
    document.querySelector('#accountTable').innerHTML=`<div class="admin-table-wrap"><table><thead><tr><th>账号 / 姓名</th><th>身份</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead><tbody>${filtered.slice((state.page-1)*8,state.page*8).map(u=>`<tr><td><b>${escape(u.username)}</b><small>${escape(u.display_name)}${Number(u.id)===Number(currentUser.id)?' · 当前账号':''}</small></td><td>${roleBadge(u.role)}</td><td>${badge(u.enabled)}</td><td>${date(u.created_at)}</td><td class="admin-actions"><button data-edit-user="${u.id}">编辑权限</button><button data-reset="${u.id}">重置密码</button>${u.role!=='admin'?'<button data-classes="'+u.id+'">设置班级</button>':''}<button data-delete-user="${u.id}" ${Number(u.id)===Number(currentUser.id)?'disabled':''}>删除账号</button></td></tr>`).join('')||empty('暂无符合条件的账号',5)}</tbody></table></div><div class="admin-pagination"><span>共 ${filtered.length} 个账号</span><button id="previousPage" ${state.page===1?'disabled':''} aria-label="上一页">←</button><span>${state.page} / ${pages}</span><button id="nextPage" ${state.page===pages?'disabled':''} aria-label="下一页">→</button></div>`;
    document.querySelectorAll('[data-edit-user]').forEach(b=>b.onclick=()=>editUser(state.users.find(u=>u.id==b.dataset.editUser)));
    document.querySelectorAll('[data-reset]').forEach(b=>b.onclick=()=>resetPassword(state.users.find(u=>u.id==b.dataset.reset)));
    document.querySelectorAll('[data-delete-user]').forEach(b=>b.onclick=()=>deleteUser(state.users.find(u=>u.id==b.dataset.deleteUser)));
    document.querySelectorAll('[data-classes]').forEach(b=>b.onclick=()=>assignClasses(state.users.find(u=>u.id==b.dataset.classes)));
    document.querySelector('#previousPage').onclick=()=>{state.page--;accountRows();};
    document.querySelector('#nextPage').onclick=()=>{state.page++;accountRows();};
  }

  function baseData() {
    const rows=state.data.filter(x=>x.kind===state.kind);
    return stats(Object.entries(kinds).map(([key,name])=>[name+'资料',state.data.filter(x=>x.kind===key).length,'已登记记录']).concat([['启用资料',state.data.filter(x=>enabled(x.enabled)).length,'当前可用']]))+
      `<div class="admin-tabs" role="tablist">${Object.entries(kinds).map(([key,name])=>`<button role="tab" aria-selected="${key===state.kind}" data-kind="${key}">${name}管理</button>`).join('')}</div><div class="admin-toolbar"><h2>${kinds[state.kind]}目录</h2><button id="newData" class="admin-primary">新增${kinds[state.kind]}</button></div><div class="admin-table-wrap"><table><thead><tr><th>编码</th><th>名称</th><th>备注</th><th>状态</th><th>更新时间</th><th>操作</th></tr></thead><tbody>${rows.map(x=>`<tr><td>${escape(x.code)}</td><td><b>${escape(x.name)}</b></td><td class="admin-details">${escape(x.details)||'-'}</td><td>${badge(x.enabled)}</td><td>${date(x.updated_at)}</td><td><button data-edit-data="${x.id}">编辑</button></td></tr>`).join('')||empty('暂无'+kinds[state.kind]+'资料',6)}</tbody></table></div>`;
  }

  function operations() {
    const s=state.operations, minutes=Math.floor(s.uptimeSeconds/60), heap=Math.min(100,Math.round(s.heapUsedMb/s.heapMaxMb*100));
    return stats([['应用服务','运行中','已连接当前服务'],['数据存储',s.demoMode?'内存体验模式':(s.database?'数据库连接正常':'数据库连接异常'),s.demoMode?'重启后恢复初始数据':s.databaseMs+' ms'],['本次运行',Math.floor(minutes/60)+'时 '+minutes%60+'分','自服务启动以来'],['内存占用',s.heapUsedMb+' MB','上限 '+s.heapMaxMb+' MB']])+`
      <section class="admin-health"><div><h2>运行检查</h2><span>检查时间：${date(s.checkedAt)}</span></div><dl><div><dt>应用连接</dt><dd class="permission-yes">正常</dd></div><div><dt>数据库查询</dt><dd>${s.database?'正常':'异常'}</dd></div><div><dt>Java 版本</dt><dd>${escape(s.javaVersion)}</dd></div><div><dt>可用处理器</dt><dd>${s.processors} 核</dd></div><div><dt>内存使用率</dt><dd><progress value="${heap}" max="100"></progress> ${heap}%</dd></div></dl></section>
      <div class="admin-toolbar"><h2>管理操作日志 <small>最近 200 条</small></h2><button id="exportAudit" ${state.audit.length?'':'disabled'}>导出日志</button></div><div class="admin-table-wrap"><table><thead><tr><th>时间</th><th>操作人</th><th>操作</th><th>对象</th><th>结果</th></tr></thead><tbody>${state.audit.map(x=>`<tr><td>${date(x.created_at)}</td><td>${escape(x.username)}</td><td>${escape(x.action)}</td><td>${escape(x.target)}</td><td><span class="admin-status is-on">成功</span></td></tr>`).join('')||empty('暂无管理操作记录',5)}</tbody></table></div>`;
  }

  function render() {
    const content=document.querySelector('#adminContent');
    content.innerHTML=state.view==='accounts'?accounts():state.view==='base'?baseData():operations();
    document.querySelectorAll('[data-tab]').forEach(b=>b.onclick=()=>{state.tab=b.dataset.tab;render();});
    if(state.view==='accounts'&&state.tab==='users'){
      accountRows(); document.querySelector('#newUser').onclick=()=>editUser();
      document.querySelector('#adminSearch').oninput=e=>{state.query=e.target.value;state.page=1;accountRows();};
      document.querySelector('#adminRole').onchange=e=>{state.role=e.target.value;state.page=1;accountRows();};
      document.querySelector('#adminStatus').onchange=e=>{state.status=e.target.value;state.page=1;accountRows();};
    }
    if(state.view==='base'){
      document.querySelectorAll('[data-kind]').forEach(b=>b.onclick=()=>{state.kind=b.dataset.kind;render();});
      document.querySelector('#newData').onclick=()=>editData();
      document.querySelectorAll('[data-edit-data]').forEach(b=>b.onclick=()=>editData(state.data.find(x=>x.id==b.dataset.editData)));
    }
    if(state.view==='operations') document.querySelector('#exportAudit').onclick=()=>{
      const cell=x=>'"'+String(x??'').replace(/^[=+@-]/,"'$&").replace(/"/g,'""')+'"';
      const rows=[['时间','操作人','操作','对象'],...state.audit.map(x=>[date(x.created_at),x.username,x.action,x.target])];
      const blob=new Blob(['\uFEFF'+rows.map(row=>row.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'});
      const a=document.createElement('a'); a.href=URL.createObjectURL(blob);a.download='管理操作日志.csv';a.click();setTimeout(()=>URL.revokeObjectURL(a.href),1000);
    };
  }

  async function load() {
    const id=++requestId, view=state.view;
    const content=document.querySelector('#adminContent');content.innerHTML='<p class="admin-loading" role="status">正在加载管理数据…</p>';
    try {
      if(view==='accounts'){const [users,config]=await Promise.all([request('/users'),fetch('/api/auth/config').then(r=>{if(!r.ok)throw new Error('无法读取运行模式');return r.json();})]);if(id!==requestId)return;state.users=users;state.demoMode=config.demoMode;}
      if(view==='base'){const data=await request('/base-data');if(id!==requestId)return;state.data=data;}
      if(view==='operations'){
        const [status,audit]=await Promise.all([request('/operations'),request('/audit')]);
        if(id!==requestId)return;state.operations=status;state.audit=audit;
      }
      if(id===requestId)render();
    } catch(e) {
      if(id!==requestId)return;
      content.innerHTML=`<div class="admin-load-error" role="alert"><h2>数据加载失败</h2><p>${escape(e.message)}</p><button id="retryAdmin">重新加载</button></div>`;
      document.querySelector('#retryAdmin').onclick=load;
    }
  }

  function page(view) {
    state.view=view;
    document.querySelectorAll('nav [data-admin-view]').forEach(b=>{
      b.classList.toggle('active',b.dataset.adminView===view);
      if(b.dataset.adminView===view)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');
    });
    const title={accounts:'账号权限',base:'基础数据',operations:'系统运维'}[view];
    document.querySelector('main').innerHTML=`<section class="admin-workspace"><div class="admin-heading"><div><p>星河智学 / 管理后台</p><h1>${title}</h1></div><button id="refreshAdmin">${view==='operations'?'重新检查':'刷新数据'}</button></div><div id="adminContent"></div><footer class="admin-footer">星河智学 · 管理后台<span>${new Date().toLocaleDateString('zh-CN')}</span></footer></section>`;
    document.querySelector('#refreshAdmin').onclick=load;load();
  }

  window.renderAdminDashboard=()=>{
    document.body.classList.add('admin-mode');
    document.querySelector('nav').innerHTML='<button data-admin-view="accounts">账号权限</button><button data-admin-view="base">基础数据</button><button data-admin-view="operations">系统运维</button>';
    document.querySelectorAll('nav [data-admin-view]').forEach(b=>b.onclick=()=>page(b.dataset.adminView));
    page('accounts');
  };
})();
