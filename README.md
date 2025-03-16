## 📌 What is This?
This is a **Reflection-Based Testing Framework** that **automates testing** in Java applications **without needing JUnit**. Instead of writing separate test files, you define test cases **directly in your code** using **annotations**.

### 🔥 Why Use This?
- 🚀 **No Need for JUnit** – Define and run tests without extra dependencies.
- 🎯 **Built-in Mocking Support** – Uses Mockito to mock dependencies.
- ⚡ **Faster Execution** – Tests run directly using **Java Reflection**.
- 🛠️ **Easy Test Discovery** – The framework **automatically finds and runs tests**.

## 🛠️ How Does It Work?
1. **You annotate methods** in your code with `@ExpectedResult`, specifying test inputs and expected outputs.
2. **The TestRunner scans your project**, finds all annotated test methods, and executes them.
3. **It compares actual vs. expected results** and reports pass/fail.
4. **Mocks are automatically injected** using `@MockDependency`, allowing you to test methods that rely on external services.

### ✅ Example: Writing a Test
Instead of using JUnit, you **just add annotations**:

```java
@ExpectedResult(inputJson = "[5, 3]", expectedJson = "8")
@ExpectedResult(inputJson = "[0, 0]", expectedJson = "0")
public int addTwoNumbers(int a, int b) {
    return a + b;
}
```
The **TestRunner** will automatically discover this function, run it with different inputs, and validate its outputs.

## 🔗 How to Install and Run
### 📌 Prerequisites
📌 **Requirements:**
- **Java 17+**
- **Maven** (for dependencies)

### 📥 Installation
Clone the repository:

```bash
git clone https://github.com/wdhurleyjr/reflection-test-framework.git
cd reflection-test-framework
```
### ▶️ Running Tests
To execute all **annotated tests**, run:

```bash
mvn compile exec:java -Dexec.mainClass="com.reflectiontest.springReflectionTest.TestRunner
```
The framework will **scan your project, execute all tests, and display results**.

## 🤖 How Does Mocking Work?
This framework **supports mocking** without JUnit by using a custom `@MockDependency` annotation.

### Example: Mocking an External Repository

```java
@MockDependency public ExternalProductRepository productRepository;
```
Mocks are **automatically injected** before tests run, allowing you to test methods that depend on external services **without actually calling them**.

## 📊 Reflection Testing vs. JUnit

| **Feature**          | **Reflection-Based**  | **JUnit** |
|----------------------|----------------------|-----------|
| **Test Definition**  | ✅ **Annotations in Code** | ❌ Separate Test Classes |
| **Dependency-Free**  | ✅ No JUnit Required | ❌ Requires JUnit |
| **Mocking Support**  | ✅ Uses Mockito | ✅ Uses Mockito |
| **Execution Speed**  | ⚡ **Faster 30X** | 🐢 Slower |
| **Ease of Use**      | ✅ Simple, No Setup | ❌ Requires Config |

### ⚡ **Speed Advantage**
This framework runs **faster than JUnit** because:
- **No test class instantiation overhead**.
- **Direct method invocation via reflection**.
- **No dependency on Spring TestContext**.

🚀 **Optimized for performance!**

## 📊 Benchmark: Reflection-Based Testing vs. JUnit

We compared the **execution speed** of this **Reflection-Based Testing Framework** against **JUnit (Maven Surefire with Mockito and Spring Boot)**.

### 🕒 Total Execution Time
| Test Framework          | Total Execution Time | Overhead |
|-------------------------|---------------------|----------|
| **Reflection-Based (Custom)** | **~100ms** | **Minimal** |
| **JUnit (Maven Surefire, Mockito, Spring Boot)** | **3.279 seconds** | **High (Spring Context, DI, Mock Setup)** |

---

### 🚀 Individual Test Performance Comparison
| Test Case                  | Reflection-Based Test Time | JUnit Test Time |
|----------------------------|--------------------------|----------------|
| `getProductDetails("Laptop")` | **74ms** | **536ms** |
| `addTwoNumbers(5,3)`        | **17ms** | **19ms** |
| `divideTwoNumbers(10.0,2.0)` | **19ms** | **10ms** |
| **All Other Tests**         | **0-1ms** | **Varied (3-32ms)** |

---

## 🔍 Why is Reflection-Based Testing Faster?
✅ **No JUnit Overhead** – No need to spin up a test context.  
✅ **No Spring Boot Context Loading** – Skips the heavyweight Spring Boot startup process.  
✅ **Direct Reflection Invocation** – Calls methods dynamically without extra test classes.  
✅ **Built-in Mocking with Less Overhead** – Injects mocks directly without Mockito’s proxy wrapping.  

---

## 🔥 Conclusion
This **Reflection-Based Testing Framework** executes **~30x faster** than traditional JUnit tests in this benchmark. It is **ideal for microservices, rapid iteration, and performance-sensitive applications**.
