# 星河智学 · 高中个性化教学平台

## 直接启动（无需安装或配置数据库）

需要 JDK 17 或更高版本。在 IDEA 中以 Maven 项目打开本目录，等待依赖下载完成，运行 XingheZhixueApplication，访问 http://localhost:8090/ 。无需提前建库、建表或导入数据。

也可以运行以下命令打包，把 jar 复制到其他安装了 JDK 17+ 的电脑运行：

```shell
mvn clean package
java -jar target/xinghe-zhixue-1.0.0.jar
```

默认只初始化三个账号，密码均为 123456：

| 账号 | 身份 |
| --- | --- |
| student | 学生 |
| teacher | 老师 |
| admin | 管理员 |

默认使用随程序提供的 H2 内存存储，不连接本机 MySQL。三个角色都可直接登录，管理后台可查看账号、维护基础资料和检查运行状态。体验模式不允许注册或新增账号；所有修改只在本次运行期间有效，重启后恢复三个初始账号和初始数据。

## 接入 MySQL（保存数据、增加账号）

创建一个空数据库，例如 xinghe_zhixue（UTF-8），并提供有建表、查询和写入权限的数据库账号。启动前配置下面三个环境变量，或在 IDEA 运行配置的环境变量中填写：

```powershell
$env:XINGHE_DB_URL='jdbc:mysql://localhost:3306/xinghe_zhixue?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:XINGHE_DB_USERNAME='你的数据库用户名'
$env:XINGHE_DB_PASSWORD='你的数据库密码'
java -jar target/xinghe-zhixue-1.0.0.jar
```

程序会自动建表并补齐三个初始账号，不覆盖已有账号或密码。登录页自动开放注册，管理员可以新增账号，修改持久保存到 MySQL。首次正式使用时请通过管理后台修改初始密码。体验模式的数据不会自动迁移到 MySQL。

已明确配置 MySQL 时，连接失败会报告启动错误，不会悄悄切回体验模式。要恢复免配置启动，清除 XINGHE_DB_URL、XINGHE_DB_USERNAME、XINGHE_DB_PASSWORD 及自行设置的 spring.datasource.* 覆盖项后重启。

## 验证

运行 mvn test，无需本机 MySQL。测试覆盖默认模式的三个账号登录、登录状态、管理接口、新增账号限制，以及持久存储模式下的账号创建、密码修改、权限和基础资料操作。持久存储测试使用 target 下的 H2 文件数据库；正式 MySQL 连接需要在配置实际数据库后验证。

平台包含学生总览、AI 学情诊断、个性化训练、学习趋势、学科掌握度、教师班级洞察和知识图谱演示接口。
