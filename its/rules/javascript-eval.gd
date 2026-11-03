func f(code):
	JavaScriptBridge.eval("console.log(1)")
	JavaScriptBridge.eval(code)  # Noncompliant
