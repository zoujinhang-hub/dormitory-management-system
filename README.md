# 校园住宿服务平台

两人软工实践项目的可运行起点：Spring Boot 后端 + Vue 3 前端。管理员维护宿舍、学生、公告和报修状态；学生查看自己的宿舍、公告并提交报修。项目内的数据和账号均为**课堂演示数据**，不适合直接部署到公网。

## 环境

- JDK 17 或更高版本（建议 IDEA 项目 SDK 选择 JDK 17 或 21）
- Maven 3.9+
- Node.js 20.19+ 或 22.12+

后端使用 H2 本地文件数据库，无需预先安装 MySQL。首次启动会自动写入两间宿舍、一名学生、一条公告和一条报修。数据库文件位于 `backend/data/`，已被 Git 忽略。

## 本地启动

1. 在 IDEA 中打开本仓库根目录，然后将 `backend/pom.xml` 导入为 Maven 项目。
2. IDEA 中运行 `edu.practice.housing.HousingApplication`；或者在 `backend` 目录执行：

   ```powershell
   mvn spring-boot:run
   ```

3. 在 IDEA 的另一个 PowerShell 终端切换到 `frontend` 目录，执行：

   ```powershell
   npm install
   npm run dev
   ```

4. 打开 `http://127.0.0.1:5173`。前端开发服务器会把 `/api` 请求代理到 `http://127.0.0.1:8080`。

演示账号：

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | `admin` | `Admin123!` |
| 学生 | `2026001` | `Student123!` |

这些账号仅用于本地演示。若要部署，请先更换密码，并补上正式的登录安全与部署配置。

## 已实现的功能

- 管理员：学生和宿舍的增删改查；发布、修改和删除公告；将报修单从“待受理”推进到“处理中”“已完成”；查看总览统计。
- 学生：查看分配给自己的宿舍和公告；提交报修；只查看自己的报修进度。
- 约束：房间不能超出床位数；已有入住或报修记录的房间不能删除；已有报修记录的学生不能删除；报修状态不能回退。

这是最小课堂演示版本：登录使用服务端会话，本地 H2 保存数据，没有文件上传、消息推送或生产级权限体系。

## 验证

在 `backend` 目录运行 `mvn test`，会检查匿名访问、学生权限、学生提交报修及管理员新增房间。前端在 `frontend` 目录运行 `npm run build`。

## 首次上传 GitHub

请在 **E 盘的项目根目录**、确认代码正常运行后，在 IDEA 的 PowerShell 终端执行以下命令。这个目录最初是普通空文件夹，所以只需初始化一次：

```powershell
git init -b main
git remote add origin https://github.com/zoujinhang-hub/dormitory-management-system.git
git add .
git commit -m "feat: initialize campus housing service"
git push -u origin main
```

如果 `git init -b main` 显示已经是 Git 仓库，先运行 `git status` 和 `git remote -v` 核对现状，不要重复添加 `origin`。首次推送后打开 [项目仓库](https://github.com/zoujinhang-hub/dormitory-management-system) 检查文件是否出现。

## 两人协作建议

首个提交可以作为共同基线；后续两人分别从 `main` 建分支开展实际功能改进。建议一人负责学生/宿舍模块，一人负责报修/公告与界面；每个功能用 Issue 记录需求，分支提交后发 Pull Request，由另一人评审再合并。按课程要求在 PR 标题或说明中写明提交者真实姓名，保留双方真实提交、评审记录和运行截图。

## 参考

调研了 [NiToot/DormitoryManagementSystem](https://github.com/NiToot/DormitoryManagementSystem) 与 [MxxxL/DormAdmin](https://github.com/MxxxL/DormAdmin) 的模块划分。本项目只参考了“房间、学生、公告、报修”这一常见业务范围，代码和界面为独立实现。
