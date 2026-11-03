const URL = "https://example.org"
func f(url):
	OS.shell_open("https://godotengine.org")
	OS.shell_open(URL)
	OS.shell_open(url)  # Noncompliant
