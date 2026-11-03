func f(path):
	OS.execute("ls", ["-la"])  # Noncompliant
	OS.create_process(path, [])  # Noncompliant
	OS.execute_with_pipe(path, [])  # Noncompliant
	OS.execute("/bin/sh", ["-c", path])
	other.OS.execute("ls", [])
