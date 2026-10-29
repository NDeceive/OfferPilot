"""Build a portable source package without local credentials or user data."""
import datetime
import hashlib
import importlib.util
import json
import pathlib
import re
import shutil
import zipfile
import argparse

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--zip', action='store_true')
parser.add_argument('--without-models', action='store_true', help='Digital human assets download on first use')
args = parser.parse_args()

ROOT = pathlib.Path(__file__).resolve().parents[1]
stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
OUT = ROOT / 'output' / ('local-deploy-' + stamp)
DEST = OUT / 'OfferPilot'
DEST.mkdir(parents=True)
skip = {'node_modules', 'target', 'dist', '__pycache__', '.npm-cache', 'logs', 'streamout', '.git'}

def copy(relative):
    src = ROOT / relative
    if not src.exists():
        return
    if src.is_dir():
        for p in src.rglob('*'):
            if p.is_file() and not any(x in skip or x.startswith('.venv') for x in p.relative_to(src).parts):
                if p.suffix in {'.log', '.pyc'} or '.local.' in p.name or p.name.startswith('.env') and p.name != '.env.example':
                    continue
                target = DEST / p.relative_to(ROOT)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(p, target)
    else:
        target = DEST / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, target)

for name in ['README.md', 'PRODUCT.md', 'DESIGN.md', '一键启动网页.bat',
             '后端/src', '后端/.mvn', '后端/libs', '后端/pom.xml', '后端/mvnw', '后端/mvnw.cmd',
             '前端/src', '前端/public', '前端/tests', '前端/package.json', '前端/package-lock.json',
             '前端/index.html', '前端/vite.config.js', '前端/.env.example', 'docs',
             'scripts/start-web.ps1', 'scripts/package-local-deploy.py', 'scripts/test-startup.ps1', 'infra',
             'scripts/import-practice-pack.py', 'scripts/verify-practice-pack.py',
             'scripts/build-practice-pack.py', 'scripts/build-objective-pack.py',
             'scripts/fetch-practice-sources.py', 'scripts/prepare-practice-judge.py',
             'scripts/test_practice_pack.py', 'scripts/test_objective_pack.py']:
    copy(name)
for p in (ROOT / '数字人').iterdir():
    if p.name in {'data', 'streamout', 'models'} or p.name.startswith('.venv'):
        continue
    copy(str(p.relative_to(ROOT)))
if not args.without_models:
    for name in ['models', 'data/avatars/offerpilot_interviewer_v2', 'data/avatars/wav2lip256_avatar1']:
        copy('数字人/' + name)
copy('文档输出/设计文档_连续排版优化版.docx')

DEPLOY = DEST / '部署'
DEPLOY.mkdir()
def write(name, content):
    (DEPLOY / name).write_text(content, encoding='utf-8-sig' if name.endswith(('.ps1', '.ps1.example')) else 'utf-8')

(DEST / '后端/src/main/resources/application-team.yml').write_text('''server:
  port: ${SERVER_PORT:8080}
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:127.0.0.1}:${DB_PORT:3306}/${DB_NAME:offerpilot_team}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}
''', encoding='utf-8')
write('local.env.ps1.example', '''# Copy to local.env.ps1 and fill your own settings. Do not share that file.
$env:DB_NAME = 'offerpilot_team'
$env:DB_USERNAME = 'root'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:SERVER_PORT = '8080'
$env:FRONTEND_PORT = '5173'
# Digital human is optional; false starts without Python/model dependencies.
$env:DIGITAL_HUMAN_ENABLED = 'false'
$env:DIGITAL_HUMAN_PORT = '8010'
# For a remote digital human service, set BASE_URL and disable LAUNCHER.
# $env:DIGITAL_HUMAN_BASE_URL = 'http://your-server:8010'
# $env:DIGITAL_HUMAN_LAUNCHER_ENABLED = 'false'
# $env:OFFERPILOT_PYTHON = 'C:/your-python/python.exe'
# $env:MYSQL_BIN = 'C:/your-mysql/bin/mysql.exe'
# $env:DEEPSEEK_BASE_URL = 'https://api.deepseek.com'
# $env:DEEPSEEK_MODEL = 'deepseek-flash'
# $env:ZHIPU_ASR_BASE_URL = 'https://open.bigmodel.cn/api/paas/v4'
# $env:JUDGE_ENABLED = 'true'
# $env:JUDGE_BASE_URL = 'http://your-judge:2358'
# $env:JUDGE_LANGUAGE_JAVA = 'your-installed-java-language-id'
# Password is requested securely at startup when this variable is omitted.
# $env:DB_PASSWORD = 'your-database-password'
# $env:DEEPSEEK_API_KEY = 'your-api-key'
# $env:ZHIPU_API_KEY = 'your-api-key'
''')
write('start-team.ps1', '''$ErrorActionPreference = 'Stop'
$localConfig = Join-Path $PSScriptRoot 'local.env.ps1'
if (Test-Path -LiteralPath $localConfig) { . $localConfig }
if (-not $env:DB_NAME) { $env:DB_NAME = 'offerpilot_team' }
if (-not $env:DB_USERNAME) { $env:DB_USERNAME = 'root' }
if (-not $env:DB_HOST) { $env:DB_HOST = '127.0.0.1' }
if (-not $env:DB_PORT) { $env:DB_PORT = '3306' }
if (-not $env:SERVER_PORT) { $env:SERVER_PORT = '8080' }
if (-not $env:FRONTEND_PORT) { $env:FRONTEND_PORT = '5173' }
if (-not $env:DIGITAL_HUMAN_ENABLED) { $env:DIGITAL_HUMAN_ENABLED = 'false' }
if (-not $env:DIGITAL_HUMAN_PORT) { $env:DIGITAL_HUMAN_PORT = '8010' }
if (-not $env:DIGITAL_HUMAN_BASE_URL) { $env:DIGITAL_HUMAN_BASE_URL = 'http://127.0.0.1:' + $env:DIGITAL_HUMAN_PORT }
$env:DIGITAL_HUMAN_PROXY_TARGET = $env:DIGITAL_HUMAN_BASE_URL
$env:BACKEND_PROXY_TARGET = 'http://127.0.0.1:' + $env:SERVER_PORT
if ($env:DIGITAL_HUMAN_ENABLED -eq 'false') { $env:DIGITAL_HUMAN_LAUNCHER_ENABLED = 'false' }
elseif (-not $env:DIGITAL_HUMAN_LAUNCHER_ENABLED) { $env:DIGITAL_HUMAN_LAUNCHER_ENABLED = 'true' }
$env:DIGITAL_HUMAN_LAUNCHER_WORKDIR = Join-Path (Split-Path $PSScriptRoot -Parent) '数字人'
if (-not $env:DB_PASSWORD) {
    $secret = Read-Host 'MySQL password' -AsSecureString
    $credential = New-Object System.Management.Automation.PSCredential('mysql', $secret)
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
}
if (-not $env:JWT_SECRET) {
    $keyFile = Join-Path $PSScriptRoot '.jwt-secret'
    if (Test-Path -LiteralPath $keyFile) { $env:JWT_SECRET = (Get-Content -LiteralPath $keyFile -Raw).Trim() }
    else {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    Set-Content -LiteralPath $keyFile -Value $env:JWT_SECRET -Encoding ASCII
    }
}
$env:SPRING_PROFILES_ACTIVE = 'team'
& (Join-Path (Split-Path $PSScriptRoot -Parent) 'scripts/start-web.ps1')
''')
(DEST / '启动队员版.bat').write_bytes(b'@echo off\r\ncd /d "%~dp0"\r\npowershell.exe -NoProfile -ExecutionPolicy Bypass -File "\u90e8\u7f72/start-team.ps1"\r\nif errorlevel 1 pause\r\n'.decode('unicode_escape').encode('gbk'))

db = ROOT / '后端/src/main/resources/db'
names = ['schema.sql', 'seed_skill_bank.sql', 'migration_v4_job_banks.sql',
         'migration_v4b_job_profiles.sql', 'migration_v4c_word_boundary.sql',
         'migration_v4d_tag_fix.sql', 'migration_v4e_retag.sql', 'migration_v4f_substr_fix.sql',
         'migration_v4g_pm_agile.sql', 'migration_v4h_tag_precision.sql', 'migration_v4i_alg_profile.sql',
         'migration_scoring_v1.sql', 'migration_v5_practice.sql', 'migration_v6_practice_bank.sql',
         'migration_v7_practice_workflow.sql', 'migration_v8_objective_practice.sql', 'profile.sql',
         'migration_expressions.sql', 'student_backend.sql', 'migration_teaching_linkage.sql', 'teaching_alignment.sql', 'migration_meeting.sql']
sql = """SET NAMES utf8mb4;
SET @init_lock=GET_LOCK('offerpilot_initialize',30);
SET @init_guard=IF(@init_lock=1 AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='offerpilot_team')=0,'DO 0','INITIALIZATION_ABORT_DATABASE_NOT_EMPTY_OR_LOCK_FAILED');
PREPARE init_guard FROM @init_guard;
EXECUTE init_guard;
DEALLOCATE PREPARE init_guard;
"""
for name in names:
    sql += '\n-- Source: ' + name + '\n' + re.sub(r'\bzhimian\b', 'offerpilot_team', (db / name).read_text(encoding='utf-8-sig')) + '\n'
sql += "\nALTER TABLE job_position ADD COLUMN icon_key VARCHAR(80) NULL, ADD COLUMN theme_key VARCHAR(40) NULL;\n"
sql += (db / 'migration_v3_job_icons.sql').read_text(encoding='utf-8-sig')
spec = importlib.util.spec_from_file_location('practice_importer', ROOT / 'scripts/import-practice-pack.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
for name in ['pilot.json', 'objective.json']:
    pack = json.loads((ROOT / '后端/src/main/resources/practice-bank' / name).read_text(encoding='utf-8'))
    sql += '\n' + module.sql_for(pack) + '\n'
sql += "SELECT RELEASE_LOCK('offerpilot_initialize');\n"
write('init.sql', sql)
write('init_database.py', '''"""Initialize an empty database; refuse to replace existing tables."""
import argparse, getpass, os, pathlib, re, shutil, subprocess
p = argparse.ArgumentParser()
p.add_argument('--database', default=os.getenv('DB_NAME', 'offerpilot_team'))
p.add_argument('--user', default=os.getenv('DB_USERNAME', 'root'))
p.add_argument('--host', default=os.getenv('DB_HOST', '127.0.0.1'))
p.add_argument('--port', default=os.getenv('DB_PORT', '3306'))
a = p.parse_args()
if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_]{0,63}', a.database) or a.database.lower() in {'mysql','sys','information_schema','performance_schema'}:
    p.error('Choose a dedicated application database name')
mysql = os.getenv('MYSQL_BIN') or shutil.which('mysql')
if not mysql:
    candidates = list(pathlib.Path(os.getenv('ProgramFiles', 'C:/Program Files')).glob('MySQL/MySQL Server */bin/mysql.exe'))
    mysql = str(sorted(candidates)[-1]) if candidates else None
if not mysql:
    p.error('Install MySQL client or set MYSQL_BIN to its executable path')
env = dict(os.environ)
env['MYSQL_PWD'] = os.getenv('DB_PASSWORD') or getpass.getpass('MySQL password: ')
sql = pathlib.Path(__file__).with_name('init.sql').read_text(encoding='utf-8')
sql = re.sub(r'\\bofferpilot_team\\b', a.database, sql)
result = subprocess.run([mysql, '--host='+a.host, '--port='+a.port, '--user='+a.user, '--default-character-set=utf8mb4', '--batch'], input=sql.encode('utf-8'), env=env)
if result.returncode:
    raise SystemExit(result.returncode)
print('Database initialized: '+a.database)
''')
write('本地部署说明.md', '''# OfferPilot 队员本地部署

本包包含当前前后端源码、题库、数字人模型与两个默认头像资源。未包含本机密钥、真实用户数据库、个人简历、日志或已安装依赖。练习页面保留当前前端样例模式；编程题采用粗略检查，结算仅用于功能演示。

## Windows 首次部署

1. 完整解压到本地目录，推荐短路径。安装 JDK 17 或 21（包含 javac）、Node.js 22.12+、MySQL 8.0+、Python 3.10，将命令加入 PATH。启动本机 MySQL，端口 3306。
2. 在解压后的 OfferPilot 目录打开 PowerShell，执行：

```powershell
python .\\部署\\init_database.py
```

输入自己的 MySQL root 密码。脚本创建独立的 `offerpilot_team` 数据库，导入基础数据和题库；目标库已有表时拒绝初始化，避免覆盖。不要直接逐个运行旧迁移 SQL。

3. 如使用非 root 账号或自定义数据库，将 `部署/local.env.ps1.example` 复制为 `部署/local.env.ps1`，修改 DB_USERNAME、DB_NAME。初始化时对应执行 `python .\\部署\\init_database.py --database 数据库名 --user 用户名`。本机配置文件不要再次转发。
4. 双击根目录 **启动队员版.bat**，输入数据库密码。首次启动自动安装 npm/Maven/Python 依赖，需要联网并预留时间。浏览器打开 `http://localhost:5173`；首次可注册学生账号。关闭启动窗口内的服务请按其提示操作。

服务端口：MySQL 3306、后端 8080、前端 5173、数字人 8010。数字人仅支持 Python 3.10，需要较多内存和磁盘空间；本包已带默认模型与头像，无需重新下载它们。不要同时启动其他占用这些端口的服务。

## AI 配置

在本机 `部署/local.env.ps1` 设置 DEEPSEEK_API_KEY、ZHIPU_API_KEY。未配置大模型密钥时可展示规则回退流程，语音识别需自己的智谱密钥；默认 EdgeTTS 需要联网。真实编译判题服务尚未启用。

## 仅前后端演示 / 手动启动

两个 PowerShell 窗口，先按上述步骤初始化数据库。后端窗口设置：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'team'
$env:DB_NAME = 'offerpilot_team'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '你自己的数据库密码'
$env:JWT_SECRET = '请替换成你自己的至少32字符随机字符串'
$env:DIGITAL_HUMAN_ENABLED = 'false'
$env:DIGITAL_HUMAN_LAUNCHER_ENABLED = 'false'
cd 后端
.\\mvnw.cmd spring-boot:run
```

前端窗口：

```powershell
cd 前端
npm ci
npm run dev
```

这种方式可展示学习资源与练习流程，数字人视频不可用。更完整说明参见根目录 README.md，但队员包请优先使用本说明的独立数据库和“启动队员版”入口。

## 排查

- 找不到 mysql：设置 `$env:MYSQL_BIN = '你的 mysql.exe 完整路径'` 后重新初始化。
- 初始化失败：查看第一条 SQL 错误；已存在表的库不会被覆盖，可改用新的空库名，并同步 DB_NAME。
- 依赖下载失败：检查网络与 Maven/npm/PyPI 连接后重试。
- 模型与题库来源/许可证保留在源码、数字人 LICENSE、题库 sources 等文件中；本包用于队员协作开发。
- `PACKAGE-MANIFEST.json` 记录文件 SHA-256；压缩包旁的 `.sha256` 可校验传输完整性。
''')

write('本地部署说明.md', '''# OfferPilot 本地部署交付包

此包是当前工作目录的源码快照，包含尚未提交的最新改动、学生/教师/企业功能、题库、会议表初始化、默认数字人模型与头像，以及最新设计文档。未包含原电脑的密钥、数据库备份、个人简历、日志、虚拟环境、node_modules 或构建缓存。依赖首次安装仍需联网；加 --without-models 打包的版本在开启数字人时还会下载模型。

## Windows 首次使用

1. 完整解压至本机目录（不要直接在 ZIP 内运行）。安装 JDK 17 或更高的完整 JDK、Node.js 20.19+ 或 22.12+、MySQL 8+。开启数字人才需要 Python 3.10。将 java、javac、node、npm、mysql 加入 PATH。
2. 将 `部署/local.env.ps1.example` 复制为 `部署/local.env.ps1`，按自己的电脑修改 DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、SERVER_PORT、FRONTEND_PORT。数据库密码可留空，初始化和启动时会分别隐藏输入。路径全部由脚本所在目录解析，支持解压到其他盘符和包含空格的路径。
3. 在解压后的 OfferPilot 目录打开 PowerShell，初始化一个独立空数据库：

```powershell
. .\\部署\\local.env.ps1
python .\\部署\\init_database.py
```

初始化脚本需要 Python 3（无需数字人的 Python 依赖），如未安装请安装 Python 3.10。找不到 MySQL 客户端时，在配置中设置 MYSQL_BIN 为自己的 mysql.exe 路径。目标数据库已有表时脚本会拒绝运行；失败时不要强行重跑旧 schema.sql，应先排查错误，再改用新的空数据库名。脚本导入的是公开题库和演示账号，不复制原电脑的用户数据。

4. 双击根目录 **启动队员版.bat**。默认关闭数字人，自动安装前端/Maven 依赖后打开网页；无需先安装 Python AI 依赖。默认访问 http://localhost:5173，端口修改后使用启动器输出的地址。使用期间保留启动窗口，按回车停止本次启动的进程。

本包优先使用“启动队员版”与本说明。原始“一键启动网页”和根 README 留作开发参考，原始入口默认配置不适用于新的独立数据库。修改配置后重启，已经运行的服务无法自动更新配置。

## 本地适配入口

| 配置 | 用途 |
| --- | --- |
| DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD | 数据库连接，与初始化脚本保持一致 |
| MYSQL_BIN | MySQL 命令行客户端完整路径 |
| SERVER_PORT / FRONTEND_PORT | 后端和网页端口；HTTP/会议 WebSocket 代理自动同步 |
| JAVA_HOME | 自己的完整 JDK 目录 |
| DIGITAL_HUMAN_ENABLED | 默认 false，可改 true 开启数字人 |
| DIGITAL_HUMAN_PORT | 同机数字人端口，默认 8010 |
| DIGITAL_HUMAN_BASE_URL / DIGITAL_HUMAN_LAUNCHER_ENABLED | 远程数字人地址；连接远程服务时关闭自动启动 |
| OFFERPILOT_PYTHON / OFFERPILOT_BATCH_SIZE | Python 3.10 完整路径、数字人批大小 |
| DEEPSEEK_API_KEY / DEEPSEEK_BASE_URL / DEEPSEEK_MODEL | 自己的大模型密钥、兼容接口地址与模型名称 |
| ZHIPU_API_KEY / ZHIPU_ASR_BASE_URL / ZHIPU_ASR_MODEL | 自己的语音识别服务 |
| JUDGE_ENABLED / JUDGE_BASE_URL / JUDGE_LANGUAGE_JAVA / JUDGE_AUTH_TOKEN | 可选的自部署 Judge0；默认关闭 |

本地 JWT 密钥首次启动自动生成并保存于 `部署/.jwt-secret`，便于重启后保持登录有效；也可自行配置 JWT_SECRET。不要将 local.env.ps1、.jwt-secret 和后端 config/ai.local.properties 再次转发。

## 可选服务与功能边界

数字人启用前安装 Python 3.10，首次运行会创建本机虚拟环境并安装依赖，已有模型和默认头像无需重复下载。默认 EdgeTTS 播报需要联网；没有可用音视频设备或浏览器权限时可使用文字训练。未配置 DeepSeek 密钥时动态追问使用规则回退；语音转写需要自己的智谱密钥。

学习资源截图中的样例模式保存在浏览器，编程结果为本地粗略检查。正式题库与作答由数据库保存；真实编译判题需另行部署 Judge0，按 `infra/practice-judge/README.md` 配置后启用。视频会议需要连接后端与 WebSocket；移动端会议页可展示入口和界面，当前会议室实现会引导到电脑浏览器，跨设备视频还取决于 HTTPS、网络和 ICE 连通性。

初始化提供 student、teacher、admin、enterprise 演示账号，密码见原 README 默认账号段（123456，仅限本地演示）。也可注册新学生。模型、题库来源及许可证保留在相关源码目录。

## macOS / Linux 手动启动

数据库初始化脚本跨平台：设置同名环境变量后运行 `python3 部署/init_database.py`。此包的自动启动器面向 Windows，其他系统在两个终端分别设置 SPRING_PROFILES_ACTIVE=team、数据库配置、JWT_SECRET，并禁用数字人自动启动。后端执行 `cd 后端 && sh mvnw spring-boot:run`；前端设置 BACKEND_PROXY_TARGET 为后端地址，执行 `cd 前端 && npm ci && npm run dev -- --host localhost --port 5173`。数字人需要单独准备 Python 环境并启动，不能调用 Windows bat。

导出报告需要可用的中文字体。Windows 默认使用系统黑体；其他系统可通过 REPORT_CJK_FONT 指定自己安装的中文 TrueType 字体路径。

## 验收与排查

先检查能登录、岗位列表有数据、个人记录可保存；分别使用 teacher 和 enterprise 登录查看对应入口，再用企业创建会议并通过学生入口查找会议号。数据库连接失败核对 host/port/name/user；端口占用时改配置，启动器不会强制终止其他程序；下载失败查看后端/logs 内日志，恢复网络后重试。

设计说明见 `文档输出/设计文档_连续排版优化版.docx`。`PACKAGE-MANIFEST.json` 记录每个文件的 SHA-256；ZIP 旁的 .sha256 用于校验转发完整性。该包供本地部署与协作开发，未包含数据库服务或已安装运行时。
''')

manifest = []
for p in sorted(DEST.rglob('*')):
    if p.is_file():
        data = p.read_bytes()
        if p.name == 'ai.local.properties' or p.name == 'local.env.ps1':
            raise RuntimeError('Local configuration leaked: ' + str(p))
        if p.suffix in {'.properties', '.yml', '.yaml', '.json', '.js', '.py', '.ps1', '.md', '.txt'} and re.search(rb'sk-[A-Za-z0-9_-]{25,}', data):
            raise RuntimeError('Possible API credential: ' + str(p.relative_to(DEST)))
        manifest.append({'path': p.relative_to(DEST).as_posix(), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
(DEST / 'PACKAGE-MANIFEST.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps({'staging': str(DEST), 'files': len(manifest), 'bytes': sum(x['bytes'] for x in manifest)}, ensure_ascii=False), flush=True)
# Zip after staging so deployment can be validated before sharing.
print('Run with --zip to create the archive after validation.', flush=True)
if args.zip:
    archive = OUT / ('OfferPilot-本地部署-' + stamp + '.zip')
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for item in manifest:
            p = DEST / item['path']
            z.write(p, p.relative_to(OUT))
        z.write(DEST / 'PACKAGE-MANIFEST.json', 'OfferPilot/PACKAGE-MANIFEST.json')
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    archive.with_suffix('.zip.sha256').write_text(digest + '  ' + archive.name + '\n', encoding='utf-8')
    print(json.dumps({'archive': str(archive), 'bytes': archive.stat().st_size, 'sha256': digest}, ensure_ascii=False))
