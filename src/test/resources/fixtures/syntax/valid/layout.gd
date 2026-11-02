# Leading comment
## Doc comment for the script; this file is indented with spaces.
extends RefCounted

#region Public API
## Doc comment for a function.
func greet(person: String) -> String:  # trailing comment
    # comment inside the body
        # deeper comment indentation is ignored
    var greeting := "Hello, %s" % person  # another

    """
    A standalone multi-line string used as a block comment.
    """
    return greeting
#endregion


func ünïcödé_identifier() -> int:
    var café := 1
    return café
