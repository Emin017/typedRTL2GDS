# Scala3 TCL DSL Examples with MLIR Lowering

本文档展示了 Scala3 TCL DSL 的使用示例以及它们如何 lower 到 MLIR 表示。

## 目录

1. [MLIR 设计概述](#mlir-设计概述)
2. [基础元素设计](#基础元素设计)
3. [Dialect 设计](#dialect-设计)
4. [类型系统](#类型系统)
5. [优化机制](#优化机制)
6. [基础变量操作](#基础变量操作)
7. [条件语句](#条件语句)
8. [循环结构](#循环结构)
9. [函数定义](#函数定义)
10. [表达式系统](#表达式系统)
11. [完整示例](#完整示例)

## MLIR 设计概述

MLIR (Multi-Level Intermediate Representation) 作为 Scala3 TCL DSL 的中间层，承担着语义表示、优化和代码生成的职责。它具有以下特点：

### 核心设计原则

1. **层次化表示**：MLIR 支持多个抽象层次的 IR，从高级语义到低级实现
2. **SSA 形式**：静态单赋值形式，便于分析和优化
3. **类型系统**：丰富的类型系统，保留 Scala3 的类型信息
4. **模块化设计**：通过 Dialect 实现特定领域的操作

### 架构层次

```
Scala3 Frontend → MLIR → TCL Backend
     ↑              ↑          ↑
类型安全          SSA形式     工具执行
宏展开           优化        简单接口
组合性          分析        命令行调用
```

## 基础元素设计

### 1. 操作（Operations）

MLIR 中的每个操作都遵循统一的结构：

```mlir
%result = operation_name(%operand1, %operand2) {attributes} : type_signature
```

- **结果值**：`%result` - 操作的输出
- **操作名**：`operation_name` - 操作的标识符
- **操作数**：`%operand1, %operand2` - 操作的输入
- **属性**：`{attributes}` - 编译时常量
- **类型签名**：`: type_signature` - 输入输出的类型

### 2. 值（Values）

在 SSA 形式中，每个值只被定义一次：

```mlir
%timeout = "eda.assign"("timeout", 10) : i32  // 定义 timeout
%sum = "eda.add"(%timeout, 5) : i32          // 使用 timeout，定义 sum
```

### 3. 区域（Regions）

区域表示一个代码块，包含一系列的基本块：

```mlir
"eda.if"(%condition) ({
  // then 区域
  ^bb0:
    "eda.puts"("True") : ()
}, {
  // else 区域
  ^bb0:
    "eda.puts"("False") : ()
}) : ()
```

### 4. 基本块（Basic Blocks）

基本块是线性指令序列，以分支或返回结束：

```mlir
^bb0(%i: i32):
  %next_i = "eda.add"(%i, 1) : i32
  "eda.br"(^bb1, %next_i) : ()

^bb1(%j: i32):
  "eda.return"(%j) : i32
```

## Dialect 设计

为了支持 TCL 语义，我们设计了一个专门的 EDA Dialect：

### 1. 核心 EDA Dialect

```mlir
// 变量操作
"eda.assign"(%name, %value) : (string, !tcl.any) -> !tcl.variable
"eda.load"(%var) : (!tcl.variable) -> !tcl.any
"eda.store"(%var, %value) : (!tcl.variable, !tcl.any) -> ()

// 控制流
"eda.if"(%cond) (%then_region, %else_region?) -> result_type
"eda.for"(%start, %end, %step) (loop_body) -> ()
"eda.while"(%cond) (loop_body) -> ()

// 函数操作
"eda.func"(@name, arguments) returns (return_types) (function_body)
"eda.call"(@func_name, args) -> result_type
"eda.return"(value) -> result_type

// 表达式操作
"eda.add"(%lhs, %rhs) : (!tcl.number, !tcl.number) -> !tcl.number
"eda.sub"(%lhs, %rhs) : (!tcl.number, !tcl.number) -> !tcl.number
"eda.mul"(%lhs, %rhs) : (!tcl.number, !tcl.number) -> !tcl.number
"eda.div"(%lhs, %rhs) : (!tcl.number, !tcl.number) -> !tcl.number
"eda.cmp_eq"(%lhs, %rhs) : (!tcl.any, !tcl.any) -> i1
"eda.cmp_gt"(%lhs, %rhs) : (!tcl.any, !tcl.any) -> i1

// 系统命令
"eda.puts"(%message) : (!tcl.string) -> ()
"eda.format"(%pattern, %args...) -> !tcl.string
"eda.concat"(%str1, %str2) -> !tcl.string
```

### 2. 文件系统 Dialect

```mlir
// 文件操作
"fs.exists"(%path) : (!tcl.string) -> i1
"fs.read"(%path) : (!tcl.string) -> !tcl.string
"fs.write"(%path, %content) : (!tcl.string, !tcl.string) -> ()
"fs.glob"(%pattern) : (!tcl.string) -> !tcl.list
```

## 类型系统

MLIR 的类型系统保留了 Scala3 的类型信息：

### 1. 基础类型

```mlir
// 数值类型
i1      // 布尔值
i8, i16, i32, i64  // 整数
f32, f64 // 浮点数

// 字符串类型
!tcl.string  // TCL 字符串

// 复合类型
!tcl.list<T>    // 列表类型
!tcl.dict<K, V> // 字典类型
```

### 2. 特殊类型

```mlir
// 变量类型（代表 TCL 变量）
!tcl.variable<T>  // 类型为 T 的变量
!tcl.any         // 任意类型

// 函数类型
(i32, !tcl.string) -> i1  // 参数类型 -> 返回类型
```

### 3. 类型转换

```mlir
// 从 Scala3 类型到 MLIR 类型映射
Scala3.Int      → i32
Scala3.Boolean  → i1
Scala3.String   → !tcl.string
TclVar[Int]     → !tcl.variable<i32>
```

## 优化机制

MLIR 提供了多种优化 pass：

### 1. 常量折叠（Constant Folding）

```mlir
// 优化前
%const_5 = "eda.constant"(5) : i32
%const_10 = "eda.constant"(10) : i32
%sum = "eda.add"(%const_5, %const_10) : i32

// 优化后
%sum = "eda.constant"(15) : i32
```

### 2. 死代码消除（Dead Code Elimination）

```mlir
// 优化前
%used = "eda.assign"("x", 10) : i32
%unused = "eda.assign"("y", 20) : i32
"eda.puts"("Value: %d", %used) : ()

// 优化后
%used = "eda.assign"("x", 10) : i32
"eda.puts"("Value: %d", %used) : ()
```

### 3. 公共子表达式消除（Common Subexpression Elimination）

```mlir
// 优化前
%temp1 = "eda.add"(%a, %b) : i32
%temp2 = "eda.add"(%a, %b) : i32
%temp3 = "eda.mul"(%temp2, 2) : i32

// 优化后
%temp1 = "eda.add"(%a, %b) : i32
%temp3 = "eda.mul"(%temp1, 2) : i32
```

### 4. 循环优化

```mlir
// 循展开（小循环）
"eda.for"(%i = 0 to 2) ({
  "eda.puts"("Iteration %d", %i) : ()
})

// 优化后
"eda.puts"("Iteration 0") : ()
"eda.puts"("Iteration 1") : ()
"eda.puts"("Iteration 2") : ()
```

### 5. 条件简化

```mlir
// 已知条件的简化
%const_true = "eda.constant"(true) : i1
"eda.if"(%const_true) ({
  "eda.puts"("Always true") : ()
}, {
  "eda.puts"("Never executed") : ()
}) : ()

// 优化后
"eda.puts"("Always true") : ()
```

## Lowering 策略

从 MLIR 到 TCL 的 lowering 包括以下步骤：

### 1. 代码生成

```mlir
// MLIR
%result = "eda.add"(%a, %b) : i32

// TCL
set result [expr {$a + $b}]
```

### 2. 顺序化

将 MLIR 的 SSA 形式转换为 TCL 的命令式形式：

```mlir
// SSA 形式
%val1 = "eda.assign"("x", 10) : i32
%val2 = "eda.add"(%val1, 5) : i32

// TCL 命令式
set x 10
set result [expr {$x + 5}]
```

### 3. 注释生成

添加调试信息和源码位置：

```tcl
set x 10 ;# From DSL.scala:10
set result [expr {$x + 5}] ;# From DSL.scala:11
```

这个 MLIR 设计为 TCL DSL 提供了强大的优化能力和清晰的语义表示，是整个架构的核心中间层。

## 目录

1. [基础变量操作](#基础变量操作)
2. [条件语句](#条件语句)
3. [循环结构](#循环结构)
4. [函数定义](#函数定义)
5. [表达式系统](#表达式系统)
6. [完整示例](#完整示例)

## 基础变量操作

### Scala3 DSL
```scala
val timeout = TclVar("timeout") := 10
val debugMode = TclVar("debug_mode") := true
val logLevel = TclVar("log_level") := "info"

// 变量引用和更新
timeout := 20
debugMode := false
```

### 生成的 MLIR
```mlir
module {
  // 变量初始化
  %timeout_init = "eda.assign"("timeout", 10) : i32
  %debug_mode_init = "eda.assign"("debug_mode", true) : i1
  %log_level_init = "eda.assign"("log_level", "info") : !tcl.string

  // 变量更新
  %timeout_new = "eda.assign"("timeout", 20) : i32
  %debug_mode_new = "eda.assign"("debug_mode", false) : i1
}
```

### 生成的 TCL
```tcl
set timeout 10
set debug_mode 1
set log_level "info"

set timeout 20
set debug_mode 0
```

## 条件语句

### Scala3 DSL
```scala
If(timeout > 15) {
  puts("Timeout is too high!")
  logLevel := "warning"
}.ElseIf(timeout === 10) {
  puts("Default timeout")
  logLevel := "info"
}.Else {
  puts("Custom timeout:", timeout.value)
}
```

### 生成的 MLIR
```mlir
module {
  %timeout_val = "eda.load"("timeout") : i32
  %cmp1 = "eda.cmp_gt"(%timeout_val, 15) : (i32, i32) -> i1
  "eda.if"(%cmp1) ({
    // then branch
    "eda.puts"("Timeout is too high!") : ()
    "eda.store"("log_level", "warning") : ()
  }, {
    %cmp2 = "eda.cmp_eq"(%timeout_val, 10) : (i32, i32) -> i1
    "eda.if"(%cmp2) ({
      // elseif branch
      "eda.puts"("Default timeout") : ()
      "eda.store"("log_level", "info") : ()
    }, {
      // else branch
      %timeout_str = "eda.to_string"(%timeout_val) : !tcl.string
      "eda.puts"("Custom timeout: %s", %timeout_str) : ()
    }) : ()
  }) : ()
}
```

### 生成的 TCL
```tcl
if {$timeout > 15} {
    puts "Timeout is too high!"
    set log_level "warning"
} elseif {$timeout == 10} {
    puts "Default timeout"
    set log_level "info"
} else {
    puts "Custom timeout: $timeout"
}
```

## 循环结构

### Scala3 DSL
```scala
For(i from 0 to 5) {
  val filename = s"log_$i.log"
  puts("Processing file:", filename)
}
```

### 生成的 MLIR
```mlir
module {
  %start = "eda.constant"(0) : i32
  %end = "eda.constant"(5) : i32
  "eda.for"(%start, %end) ({
  ^bb0(%i: i32):
    %i_str = "eda.to_string"(%i) : !tcl.string
    %filename = "eda.format"("log_%s.log", %i_str) : !tcl.string
    "eda.puts"("Processing file: %s", %filename) : ()
  }) : ()
}
```

### 生成的 TCL
```tcl
for {set i 0} {$i < 5} {incr i} {
    set filename "log_$i.log"
    puts "Processing file: $filename"
}
```

## 函数定义

### Scala3 DSL
```scala
Proc("process_file", List("filename")) { filename =>
  val exists = File.exists(filename)

  If(exists) {
    puts("Processing file:", filename)
    Return(1)
  }.Else {
    puts("File not found:", filename)
    Return(0)
  }
}

// 调用函数
val result = Call("process_file", "test.log")
```

### 生成的 MLIR
```mlir
module {
  // 函数定义
  "eda.func"(@process_file, (%filename: !tcl.string) -> i32) {
  ^bb0(%filename: !tcl.string):
    %exists = "eda.file_exists"(%filename) : i1
    "eda.if"(%exists) ({
      // then branch
      "eda.puts"("Processing file: %s", %filename) : ()
      "eda.return"(1) : i32
    }, {
      // else branch
      "eda.puts"("File not found: %s", %filename) : ()
      "eda.return"(0) : i32
    }) : i32
  }

  // 函数调用
  %filename_arg = "eda.constant"("test.log") : !tcl.string
  %result = "eda.call"(@process_file, %filename_arg) : i32
}
```

### 生成的 TCL
```tcl
proc process_file {filename} {
    if {[file exists $filename]} {
        puts "Processing file: $filename"
        return 1
    } else {
        puts "File not found: $filename"
        return 0
    }
}

set result [process_file test.log]
```

## 表达式系统

### Scala3 DSL
```scala
// 算术表达式
val sum = timeout + 5
val result = (timeout * 2) - 3
val avg = (timeout + 10) / 2

// 字符串表达式
val message = "Timeout is" + timeout.toString
val formatted = s"Debug mode: ${debugMode.value}"

// 布尔表达式
val isTimeoutHigh = timeout > 15
val isDebugEnabled = debugMode.isTrue && timeout === 10
```

### 生成的 MLIR
```mlir
module {
  // 加载变量
  %timeout = "eda.load"("timeout") : i32
  %debug = "eda.load"("debug_mode") : i1

  // 算术表达式
  %const_5 = "eda.constant"(5) : i32
  %sum = "eda.add"(%timeout, %const_5) : i32

  %const_2 = "eda.constant"(2) : i32
  %timeout_mul = "eda.mul"(%timeout, %const_2) : i32
  %const_3 = "eda.constant"(3) : i32
  %result = "eda.sub"(%timeout_mul, %const_3) : i32

  %const_10 = "eda.constant"(10) : i32
  %timeout_add = "eda.add"(%timeout, %const_10) : i32
  %const_2_div = "eda.constant"(2) : i32
  %avg = "eda.div"(%timeout_add, %const_2_div) : i32

  // 字符串表达式
  %timeout_str = "eda.to_string"(%timeout) : !tcl.string
  %const_timeout = "eda.constant"("Timeout is") : !tcl.string
  %message = "eda.concat"(%const_timeout, %timeout_str) : !tcl.string

  // 布尔表达式
  %const_15 = "eda.constant"(15) : i32
  %is_timeout_high = "eda.cmp_gt"(%timeout, %const_15) : i1

  %is_eq_10 = "eda.cmp_eq"(%timeout, %const_10) : i1
  %is_debug_enabled = "eda.and"(%debug, %is_eq_10) : i1
}
```

### 生成的 TCL
```tcl
# 算术表达式
set sum [expr {$timeout + 5}]
set result [expr {$timeout * 2 - 3}]
set avg [expr {($timeout + 10) / 2}]

# 字符串表达式
set message "Timeout is$timeout"
set formatted "Debug mode: $debug_mode"

# 布尔表达式
set isTimeoutHigh [expr {$timeout > 15}]
set isDebugEnabled [expr {$debug_mode && $timeout == 10}]
```

## 完整示例

### Scala3 DSL
```scala
val script = Script {
  // 初始化
  val timeout = TclVar("timeout") := 10
  val retryCount = TclVar("retry_count") := 0
  val debugMode = TclVar("debug_mode") := true

  // 条件逻辑
  If(timeout > 15) {
    puts("Warning: High timeout detected!")
    retryCount := 3
  }.ElseIf(timeout === 10) {
    puts("Using default timeout")
    retryCount := 2
  }

  // 循环处理
  For(i from 0 to retryCount.value) {
    val attempt = i + 1
    puts(s"Attempt $attempt of ${retryCount.value}")

    // 模拟操作
    val success = Call("attempt_operation")

    If(success === 1) {
      puts("Operation successful!")
      Break()
    }
  }

  // 函数定义
  Proc("attempt_operation") {
    // 随机成功概率
    If(Math.random() > 0.5) {
      Return(1)
    }.Else {
      Return(0)
    }
  }
}
```

### 生成的 MLIR（优化后）
```mlir
module {
  // 常量定义（优化）
  %const_10 = "eda.constant"(10) : i32
  %const_15 = "eda.constant"(15) : i32
  %const_2 = "eda.constant"(2) : i32
  %const_3 = "eda.constant"(3) : i32
  %const_true = "eda.constant"(true) : i1

  // 变量初始化
  %timeout = "eda.assign"("timeout", %const_10) : i32
  %retry_count = "eda.assign"("retry_count", 0) : i32
  %debug_mode = "eda.assign"("debug_mode", %const_true) : i1

  // 条件分支（已知 timeout=10，优化到 else if 分支）
  "eda.puts"("Using default timeout") : ()
  "eda.store"("retry_count", %const_2) : ()

  // 循环（已知 retry_count=2）
  "eda.puts"("Attempt 1 of 2") : ()
  %op1_result = "eda.call"(@attempt_operation) : i32
  %op1_success = "eda.cmp_eq"(%op1_result, %const_1) : i1
  "eda.if"(%op1_success) ({
    "eda.puts"("Operation successful!") : ()
    "eda.break"() : ()
  }, {
    // 继续循环
    "eda.puts"("Attempt 2 of 2") : ()
    %op2_result = "eda.call"(@attempt_operation) : i32
    %op2_success = "eda.cmp_eq"(%op2_result, %const_1) : i1
    "eda.if"(%op2_success) ({
      "eda.puts"("Operation successful!") : ()
      "eda.break"() : ()
    }) : ()
  }) : ()

  // 函数定义
  "eda.func"(@attempt_operation, () -> i32) {
    %rand_val = "eda.random"() : f64
    %const_05 = "eda.constant"(0.5) : f64
    %cmp = "eda.cmp_gt"(%rand_val, %const_05) : i1
    "eda.if"(%cmp) ({
      "eda.return"(1) : i32
    }, {
      "eda.return"(0) : i32
    }) : i32
  }
}
```

### 生成的 TCL
```tcl
# 初始化
set timeout 10
set retry_count 0
set debug_mode 1

# 条件逻辑
if {$timeout > 15} {
    puts "Warning: High timeout detected!"
    set retry_count 3
} elseif {$timeout == 10} {
    puts "Using default timeout"
    set retry_count 2
}

# 循环处理
for {set i 0} {$i < $retry_count} {incr i} {
    set attempt [expr {$i + 1}]
    puts "Attempt $attempt of $retry_count"

    # 模拟操作
    set success [attempt_operation]

    if {$success == 1} {
        puts "Operation successful!"
        break
    }
}

# 函数定义
proc attempt_operation {} {
    if {[expr {rand() > 0.5}]} {
        return 1
    } else {
        return 0
    }
}
```

## 关键观察

1. **类型安全**：Scala3 DSL 在编译时检查类型，确保不会对字符串做算术运算
2. **变量引用**：`timeout.value` 是真正的引用，不是字符串替换
3. **MLIR 优化**：MLIR 可以进行常量折叠（如 `timeout === 10` 在编译时已知）
4. **SSA 形式**：MLIR 使用 SSA 形式，便于分析和优化
5. **类型信息保留**：MLIR 保留了 Scala3 的类型信息，支持更精确的分析

这个示例展示了 Scala3 DSL 如何提供类型安全的编程体验，同时通过 MLIR 获得优化能力。