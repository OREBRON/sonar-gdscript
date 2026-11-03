func f(path):
	ProjectSettings.load_resource_pack(path)  # Noncompliant
	ProjectSettings.load_resource_pack("res://dlc.pck", false)  # Noncompliant
