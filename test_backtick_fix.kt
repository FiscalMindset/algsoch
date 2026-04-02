// Quick test to verify backtick conversion logic

fun main() {
    val testCases = listOf(
        // Test case from user
        """Here's a simple Python code snippet that prints Vicky Kumar: ``code python
vicky_kumar = "Vicky Kumar"
print(vicky_kumar)
``""",
        
        // Double backticks with language
        """``python
def hello():
    print("Hello")
``""",
        
        // Four backticks (excessive)
        """````python
def test():
    pass
````""",
        
        // Single inline code (should not change)
        """Use `print("hello")` to output"""
    )
    
    println("Testing backtick conversion:")
    println("="*50)
    
    testCases.forEachIndexed { index, test ->
        println("\nTest ${index + 1}:")
        println("Input: ${test.take(50)}...")
        
        var fixed = test
        
        // Remove excessive backticks
        fixed = fixed.replace(Regex("````+"), "```")
        
        // Fix ``code language pattern
        fixed = fixed.replace(Regex("``\\s*code\\s+(\\w+)\\s*\\n([\\s\\S]*?)\\n``")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }
        
        // Fix ``language pattern
        fixed = fixed.replace(Regex("``\\s*(\\w+)\\s*\\n([\\s\\S]*?)``(?!`)")) { match ->
            val language = match.groupValues[1]
            val code = match.groupValues[2].trim()
            "```$language\n$code\n```"
        }
        
        println("Output: ${fixed.take(50)}...")
        println("Has triple backticks: ${fixed.contains("```")}")
    }
}
