name: "Base PRP Template v2 - 上下文丰富且带验证循环"
description: |

## 目的
优化用于AI代理实现功能的模板，提供足够的上下文和自我验证能力，通过迭代细化实现有效代码。

## 核心原则
1. **上下文为王**：包含所有必要的文档、示例和注意事项
2. **验证循环**：提供AI可以运行和修复的可执行测试/检查
3. **信息密集**：使用代码库中的关键词和模式
4. **渐进成功**：从简单开始，验证后再增强
5. **全局规则**：确保遵循所有CLAUDIE.md中的规则

---

## 目标
[需要构建的内容 - 明确最终状态和期望]

## 为什么
- [商业价值和用户影响]
- [与现有功能的集成]
- [解决的问题及受益者]

## 什么
[用户可见的行为和技术要求]

### 成功标准
- [ ] [具体可衡量的结果]

## 所需所有上下文

### 文档和参考资料（列出实现功能所需的所有上下文）
```yaml
# 必须阅读 - 将这些内容包含在你的上下文窗口中
- url: [官方API文档URL]
  why: [需要的具体部分/方法]
  
- file: [path/to/example.py]
  why: [需要遵循的模式，需要避免的陷阱]
  
- doc: [库文档URL] 
  section: [需要的具体部分]
  critical: [防止常见错误的关键见解]

- docfile: [PRPs/ai_docs/file.md]
  why: [用户粘贴到项目中的文档]
```

### 代码库树状结构（在项目根目录运行`tree`）以获取代码库概览
```bash

```

### 带有新增文件和责任的期望代码库树状结构
```bash

```

### 我们代码库的已知陷阱和库怪癖
```python
# 关键：[库名称]需要[特定设置]
# 示例：FastAPI需要异步函数用于端点
# 示例：此ORM不支持超过1000条记录的批量插入
# 示例：我们使用pydantic v2并且
```

## 实施蓝图

### 数据模型和结构

创建核心数据模型，确保类型安全和一致性。
```python
示例：
 - ORM模型
 - pydantic模型
 - pydantic架构
 - pydantic验证器
```

### 按完成PRP所需顺序列出的任务

```yaml
任务1:
MODIFY src/existing_module.py:
  - 查找模式："class OldImplementation"
  - 在包含"def __init__"的行后插入
  - 保留现有方法签名

CREATE src/new_feature.py:
  - 镜像模式：src/similar_feature.py
  - 修改类名和核心逻辑
  - 保持错误处理模式相同

...(...)

任务N:
...
```

### 每个任务的伪代码（如需添加到每个任务中）
```python

# 任务1
# 带有关键细节的伪代码，不要编写整个代码
async def new_feature(param: str) -> Result:
    # 模式：首先总是验证输入（见src/validators.py）
    validated = validate_input(param)  # raises ValidationError
    
    # 已知问题：此库需要连接池
    async with get_connection() as conn:  # 见src/db/pool.py
        # 模式：使用现有的重试装饰器
        @retry(attempts=3, backoff=exponential)
        async def _inner():
            # 关键：API在>10 req/sec时返回429
            await rate_limiter.acquire()
            return await external_api.call(validated)
        
        result = await _inner()
    
    # 模式：标准化响应格式
    return format_response(result)  # 见src/utils/responses.py
```

### 集成点
```yaml
DATABASE:
  - 迁移："在users表中添加'feature_enabled'列"
  - 索引："CREATE INDEX idx_feature_lookup ON users(feature_id)"
  
CONFIG:
  - 添加到：config/settings.py
  - 模式："FEATURE_TIMEOUT = int(os.getenv('FEATURE_TIMEOUT', '30'))"
  
ROUTES:
  - 添加到：src/api/routes.py  
  - 模式："router.include_router(feature_router, prefix='/feature')"
```

## 验证循环

### 级别1：语法和风格
```bash
# 首先运行这些 - 在继续之前修复任何错误
ruff check src/new_feature.py --fix  # 尽可能自动修复
mypy src/new_feature.py              # 类型检查

# 期望：无错误。如果出现错误，请阅读错误并修复。
```

### 级别2：每个新功能/文件/函数的单元测试，使用现有测试模式
```python
# 创建test_new_feature.py，包含这些测试用例：
def test_happy_path():
    """基本功能正常工作"""
    result = new_feature("valid_input")
    assert result.status == "success"

def test_validation_error():
    """无效输入引发ValidationError"""
    with pytest.raises(ValidationError):
        new_feature("")

def test_external_api_timeout():
    """优雅处理超时"""
    with mock.patch('external_api.call', side_effect=TimeoutError):
        result = new_feature("valid")
        assert result.status == "error"
        assert "timeout" in result.message
```

```bash
# 运行并迭代直到通过：
uv run pytest test_new_feature.py -v
# 如果失败：阅读错误，理解根本原因，修复代码，重新运行（永远不要为了通过而打补丁）
```

### 级别3：集成测试
```bash
# 启动服务
uv run python -m src.main --dev

# 测试端点
curl -X POST http://localhost:8000/feature \
  -H "Content-Type: application/json" \
  -d '{"param": "test_value"}'

# 期望：{"status": "success", "data": {...}}
# 如果出错：检查日志中的logs/app.log以获取堆栈跟踪
```

## 最终验证检查清单
- [ ] 所有测试通过：`uv run pytest tests/ -v`
- [ ] 无代码风格错误：`uv run ruff check src/`
- [ ] 无类型错误：`uv run mypy src/`
- [ ] 手动测试成功：[具体curl命令]
- [ ] 错误情况处理得当
- [ ] 日志信息丰富但不过于冗长
- [ ] 如有需要，更新文档

---

## 需避免的反模式
- ❌ 当现有模式有效时不要创建新模式
- ❌ 不要跳过验证，因为“它应该有效”
- ❌ 不要忽略失败的测试 - 修复它们
- ❌ 不要在异步上下文中使用同步函数
- ❌ 不要硬编码应该由配置管理的值
- ❌ 不要捕获所有异常 - 要具体
