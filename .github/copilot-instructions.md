# 项目说明：weixin-ilink-sdk

## 项目目标

本项目旨在使用 Java 语言实现微信 iLink 协议的 SDK，提供对 iLink 协议的完整封装，便于 Java 应用程序与微信 iLink 服务进行通信。

## 技术栈

- **语言**: Java 17
- **构建工具**: Maven
- **包名**: `com.github.morningwn`

## 编码规范

- 使用 Java 17 语法特性（sealed classes、records、pattern matching 等）
- 遵循标准 Java 命名规范（类名 PascalCase，方法/变量 camelCase，常量 UPPER_SNAKE_CASE）
- 协议字段命名应与 iLink 协议文档保持一致
- 所有公共 API 需要编写 Javadoc 注释
- 网络通信中的字节序处理需明确标注（大端/小端）
- 协议解析相关代码应具有良好的错误处理和边界检查

## 项目结构

```
com.github.morningwn
├── protocol    # iLink 协议定义（消息类型、数据结构、常量）
├── codec       # 编解码器（序列化/反序列化）
├── client      # 客户端实现（连接管理、会话管理）
├── handler     # 消息处理器
├── exception   # 自定义异常
└── util        # 工具类
```

## 开发原则

- 协议实现应严格遵循 iLink 协议规范，不做非必要的扩展
- 优先保证协议解析的正确性和健壮性
- 网络 I/O 相关代码应考虑异步和非阻塞设计
- 敏感数据（密钥、token 等）不得硬编码或写入日志
- 单元测试应覆盖协议编解码的各种边界情况
