name: "多智能体系统：带有邮件草稿子代理的研究代理"
description: |

## 目的
构建一个 Pydantic AI 多智能体系统，主研究代理使用 Brave 搜索 API，并将邮件草稿代理（基于 Gmail API）作为工具。这展示了“代理即工具”模式与外部 API 集成的能力。

## 核心原则
1. **上下文为王**：包含所有必要的文档、示例和注意事项
2. **验证循环**：提供可执行的测试/代码检查，AI 可运行并修复
3. **信息密集**：使用代码库中的关键词和模式
4. **渐进式成功**：从简单开始，验证后逐步增强

---

## 目标
创建一个可用于生产的多智能体系统，用户可通过 CLI 研究主题，研究代理可将邮件草稿任务委托给邮件草稿代理。系统应支持多种 LLM 提供商，并安全处理 API 认证。

## 为什么
- **业务价值**：自动化研究与邮件草稿工作流
- **集成性**：展示高级 Pydantic AI 多智能体模式
- **解决问题**：减少基于研究的邮件沟通中的人工操作

## 实现内容
一个基于 CLI 的应用，具备以下功能：
- 用户输入研究查询
- 研究代理通过 Brave API 搜索
- 研究代理可调用邮件草稿代理创建 Gmail 草稿
- 结果实时流式返回给用户

### 成功标准
- [ ] 研究代理可通过 Brave API 成功搜索
- [ ] 邮件代理可通过认证创建 Gmail 草稿
- [ ] 研究代理可作为工具调用邮件代理
- [ ] CLI 提供带工具可见性的流式响应
- [ ] 所有测试通过，代码符合质量标准

## 所需全部上下文

### 文档与参考
```yaml
# 必读 - 请将这些内容纳入上下文窗口
- url: https://ai.pydantic.dev/agents/
  why: 核心代理创建模式

- url: https://ai.pydantic.dev/multi-agent-applications/
  why: 多智能体系统模式，尤其是代理即工具

- url: https://developers.google.com/gmail/api/guides/sending
  why: Gmail API 认证与草稿创建

- url: https://api-dashboard.search.brave.com/app/documentation
  why: Brave 搜索 API REST 接口文档

- file: examples/agent/agent.py
  why: 代理创建、工具注册、依赖注入模式

- file: examples/agent/providers.py
  why: 多 LLM 提供商配置模式

- file: examples/cli.py
  why: CLI 结构，流式响应与工具可见性

- url: https://github.com/googleworkspace/python-samples/blob/main/gmail/snippet/send%20mail/create_draft.py
  why: 官方 Gmail 草稿创建示例
```

### 当前代码结构
```bash
.
├── examples/
│   ├── agent/
│   │   ├── agent.py
│   │   ├── providers.py
│   │   └── ...
│   └── cli.py
├── PRPs/
│   └── templates/
│       └── prp_base.md
├── INITIAL.md
├── CLAUDE.md
└── requirements.txt
```

### 期望的代码结构及新增文件
```bash
.
├── agents/
│   ├── __init__.py               # 包初始化
│   ├── research_agent.py         # 主代理，集成 Brave 搜索
│   ├── email_agent.py            # 子代理，集成 Gmail 能力
│   ├── providers.py              # LLM 提供商配置
│   └── models.py                 # Pydantic 数据校验模型
├── tools/
│   ├── __init__.py               # 包初始化
│   ├── brave_search.py           # Brave 搜索 API 集成
│   └── gmail_tool.py             # Gmail API 集成
├── config/
│   ├── __init__.py               # 包初始化
│   └── settings.py               # 环境与配置管理
├── tests/
│   ├── __init__.py               # 包初始化
│   ├── test_research_agent.py    # 研究代理测试
│   ├── test_email_agent.py       # 邮件代理测试
│   ├── test_brave_search.py      # Brave 搜索工具测试
│   ├── test_gmail_tool.py        # Gmail 工具测试
│   └── test_cli.py               # CLI 测试
├── cli.py                        # CLI 接口
├── .env.example                  # 环境变量模板
├── requirements.txt              # 依赖包
├── README.md                     # 完整文档
└── credentials/.gitkeep          # Gmail 凭证目录
```

### 已知注意事项与库特性
```python
# 关键：Pydantic AI 全程需使用 async，异步上下文中禁止同步函数
# 关键：Gmail API 首次运行需 OAuth2 流程，需 credentials.json
# 关键：Brave API 免费额度每月 2000 次请求
# 关键：代理即工具模式需传递 ctx.usage 以便 token 统计
# 关键：Gmail 草稿需 base64 编码并正确 MIME 格式
# 关键：始终使用绝对导入，代码更清晰
# 关键：敏感凭证存储于 .env，切勿提交
```

## 实现蓝图

### 数据模型与结构

```python
# models.py - 核心数据结构
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime

class ResearchQuery(BaseModel):
    query: str = Field(..., description="要研究的主题")
    max_results: int = Field(10, ge=1, le=50)
    include_summary: bool = Field(True)

class BraveSearchResult(BaseModel):
    title: str
    url: str
    description: str
    score: float = Field(0.0, ge=0.0, le=1.0)

class EmailDraft(BaseModel):
    to: List[str] = Field(..., min_items=1)
    subject: str = Field(..., min_length=1)
    body: str = Field(..., min_length=1)
    cc: Optional[List[str]] = None
    bcc: Optional[List[str]] = None

class ResearchEmailRequest(BaseModel):
    research_query: str
    email_context: str = Field(..., description="生成邮件的上下文")
    recipient_email: str
```

### 任务清单

```yaml
任务 1：配置与环境搭建
创建 config/settings.py：
  - 模式：使用 pydantic-settings，类似示例用 os.getenv
  - 加载环境变量并设默认值
  - 校验所需 API key 是否存在

创建 .env.example：
  - 包含所有所需环境变量及说明
  - 参考 examples/README.md 模板

任务 2：实现 Brave 搜索工具
创建 tools/brave_search.py：
  - 模式：异步函数，参考 examples/agent/tools.py
  - 用 httpx 实现简单 REST 客户端
  - 优雅处理限流与错误
  - 返回结构化 BraveSearchResult 模型

任务 3：实现 Gmail 工具
创建 tools/gmail_tool.py：
  - 模式：参考 Gmail quickstart 的 OAuth2 流程
  - token.json 存储于 credentials/ 目录
  - 草稿需正确 MIME 编码
  - 自动处理认证刷新

任务 4：创建邮件草稿代理
创建 agents/email_agent.py：
  - 模式：参考 examples/agent/agent.py 结构
  - 使用 Agent + deps_type 模式
  - 注册 gmail_tool 为 @agent.tool
  - 返回 EmailDraft 模型

任务 5：创建研究代理
创建 agents/research_agent.py：
  - 模式：Pydantic AI 多代理模式
  - 注册 brave_search 为工具
  - 注册 email_agent.run() 为工具
  - 用 RunContext 注入依赖

任务 6：实现 CLI 接口
创建 cli.py：
  - 模式：参考 examples/cli.py 流式输出
  - 彩色输出，工具可见
  - 正确处理异步（asyncio.run）
  - 会话管理

任务 7：添加全面测试
创建 tests/：
  - 模式：仿照示例测试结构
  - Mock 外部 API
  - 测试主流程、边界、异常
  - 覆盖率 80% 以上

任务 8：编写文档
创建 README.md：
  - 模式：参考 examples/README.md
  - 包含安装、使用、API key 配置
  - 架构图
```

### 每项任务伪代码

```python
# 任务 2：Brave 搜索工具
async def search_brave(query: str, api_key: str, count: int = 10) -> List[BraveSearchResult]:
    # 模式：用 httpx，类似示例用 aiohttp
    async with httpx.AsyncClient() as client:
        headers = {"X-Subscription-Token": api_key}
        params = {"q": query, "count": count}
        
        # 注意：API key 无效时 Brave API 返回 401
        response = await client.get(
            "https://api.search.brave.com/res/v1/web/search",
            headers=headers,
            params=params,
            timeout=30.0  # 关键：设置超时，避免阻塞
        )
        
        # 模式：结构化错误处理
        if response.status_code != 200:
            raise BraveAPIError(f"API 返回 {response.status_code}")
        
        # 用 Pydantic 解析校验
        data = response.json()
        return [BraveSearchResult(**result) for result in data.get("web", {}).get("results", [])]

# 任务 5：研究代理调用邮件代理
@research_agent.tool
async def create_email_draft(
    ctx: RunContext[AgentDependencies],
    recipient: str,
    subject: str,
    context: str
) -> str:
    """基于研究上下文创建邮件草稿。"""
    # 关键：传递 usage 统计 token
    result = await email_agent.run(
        f"为 {recipient} 创建一封关于：{context} 的邮件",
        deps=EmailAgentDeps(subject=subject),
        usage=ctx.usage  # 多代理文档推荐模式
    )
    
    return f"草稿已创建，ID: {result.data}"
```

### 集成点
```yaml
环境变量：
  - 添加到 .env
  - 变量示例：
      # LLM 配置
      LLM_PROVIDER=openai
      LLM_API_KEY=sk-...
      LLM_MODEL=gpt-4
      
      # Brave 搜索
      BRAVE_API_KEY=BSA...
      
      # Gmail（凭证路径）
      GMAIL_CREDENTIALS_PATH=./credentials/credentials.json
      
配置：
  - Gmail OAuth：首次运行需浏览器授权
  - Token 存储：./credentials/token.json（自动生成）
  
依赖：
  - 更新 requirements.txt，需包含：
    - google-api-python-client
    - google-auth-httplib2
    - google-auth-oauthlib
```

## 验证循环

### 第一层：语法与风格
```bash
# 首先运行这些，修复所有错误
ruff check . --fix              # 自动修复风格问题
mypy .                          # 类型检查

# 期望：无错误。如有错误，需修复。
```

### 第二层：单元测试
```python
# test_research_agent.py
async def test_research_with_brave():
    """测试研究代理能否正确搜索"""
    agent = create_research_agent()
    result = await agent.run("AI 安全研究")
    assert result.data
    assert len(result.data) > 0

async def test_research_creates_email():
    """测试研究代理能否调用邮件代理"""
    agent = create_research_agent()
    result = await agent.run(
        "研究 AI 安全并为 john@example.com 草拟邮件"
    )
    assert "draft_id" in result.data

# test_email_agent.py  
def test_gmail_authentication(monkeypatch):
    """测试 Gmail OAuth 流程"""
    monkeypatch.setenv("GMAIL_CREDENTIALS_PATH", "test_creds.json")
    tool = GmailTool()
    assert tool.service is not None

async def test_create_draft():
    """测试草稿创建及编码"""
    agent = create_email_agent()
    result = await agent.run(
        "为 test@example.com 创建关于 AI 研究的邮件"
    )
    assert result.data.get("draft_id")
```

```bash
# 反复运行测试直到全部通过：
pytest tests/ -v --cov=agents --cov=tools --cov-report=term-missing

# 如失败：定位具体测试，修复后重试
```

### 第三层：集成测试
```bash
# 测试 CLI 交互
python cli.py

# 期望交互：
# 你：研究最新 AI 安全进展
# 🤖 助手：[流式输出研究结果]
# 🛠 使用工具：
#   1. brave_search (query='AI 安全进展', limit=10)
#
# 你：为 john@example.com 草拟邮件  
# 🤖 助手：[创建草稿]
# 🛠 使用工具：
#   1. create_email_draft (recipient='john@example.com', ...)

# 检查 Gmail 草稿箱是否有新草稿
```

## 最终验证清单
- [ ] 所有测试通过：`pytest tests/ -v`
- [ ] 无风格错误：`ruff check .`
- [ ] 无类型错误：`mypy .`
- [ ] Gmail OAuth 流程正常（浏览器弹出，token 保存）
- [ ] Brave 搜索返回结果
- [ ] 研究代理可成功调用邮件代理
- [ ] CLI 可流式输出并显示工具
- [ ] 错误情况处理优雅
- [ ] README 有清晰的安装说明
- [ ] .env.example 包含所有必需变量

---

## 反模式警告
- ❌ 不要硬编码 API key，务必用环境变量
- ❌ 不要在异步代理中用同步函数
- ❌ 不要跳过 Gmail OAuth 流程
- ❌ 不要忽略 API 限流
- ❌ 不要忘记多代理调用时传递 ctx.usage
- ❌ 不要提交 credentials.json 或 token.json

## 信心评分：9/10

高信心原因：
- 代码库有清晰示例可参考
- 外部 API 文档完善
- 多智能体系统模式成熟
- 验证流程全面

Gmail OAuth 首次体验略有不确定，但官方文档有详细指引。