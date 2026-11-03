const SCRIPT = "echo fixed"
func f(command):
	OS.execute("/bin/sh", ["-c", command])  # Noncompliant
	OS.execute("cmd.exe", ["/C", command])  # Noncompliant
	OS.execute("bash", ["-c", SCRIPT])
	OS.execute("ls", [command])
