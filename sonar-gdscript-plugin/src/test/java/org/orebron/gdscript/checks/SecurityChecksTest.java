package org.orebron.gdscript.checks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.orebron.gdscript.lexer.GDScriptLexer;
import org.orebron.gdscript.metrics.SizeMetrics;
import org.orebron.gdscript.parser.GDScriptParser;
import org.sonar.api.rules.RuleType;

/** The security plan's L1 review rules, each with matching code, safe code and a near miss. */
class SecurityChecksTest {
  @Test void processCallsAndShellCommands() throws Exception {
    String source = """
        const SCRIPT = "echo fixed"
        func f(command, path):
        \tOS.execute("ls", ["-la"])
        \tOS.execute("/bin/sh", ["-c", command])
        \tOS.execute("cmd.exe", ["/C", SCRIPT])
        \tOS.create_process(path, [])
        \tother.OS.execute("sh", ["-c", command])
        """;
    var file = parse(source);
    assertEquals(List.of(3, 4, 5, 6), lines(new SecurityChecks.ProcessExecutionCheck().check(file, 0)));
    assertEquals(List.of(4), lines(new SecurityChecks.ShellCommandCheck().check(file, 0)));
    var both = new java.util.ArrayList<Violation>(new SecurityChecks.ProcessExecutionCheck().check(file, 0));
    both.addAll(new SecurityChecks.ShellCommandCheck().check(file, 0));
    var kept = Checks.withPrecedence(both);
    assertEquals(List.of("process-execution:3", "shell-command:4", "process-execution:5", "process-execution:6"),
        kept.stream().sorted(java.util.Comparator.comparingInt(Violation::line)).map(v -> v.rule().rule() + ":" + v.line()).toList());
  }

  @Test void dynamicArgumentsOnly() throws Exception {
    String source = """
        const URL = "https://example.org"
        func f(url, code):
        \tOS.shell_open("https://godotengine.org")
        \tOS.shell_open(URL)
        \tOS.shell_open(url)
        \tJavaScriptBridge.eval("console.log(1)")
        \tJavaScriptBridge.eval(code)
        """;
    var file = parse(source);
    assertEquals(List.of(5), lines(new SecurityChecks.ShellOpenCheck().check(file, 0)));
    assertEquals(List.of(7), lines(new SecurityChecks.JavaScriptEvalCheck().check(file, 0)));
  }

  @Test void namesResolveInTheirOwnScope() throws Exception {
    String source = """
        func trusted():
        \tconst payload = "fixed"
        \tvar OS = null
        \tJavaScriptBridge.eval(payload)
        func run(payload):
        \tJavaScriptBridge.eval(payload)
        \tOS.execute("ls", [])
        """;
    var file = parse(source);
    assertEquals(List.of(6), lines(new SecurityChecks.JavaScriptEvalCheck().check(file, 0)));
    assertEquals(List.of(7), lines(new SecurityChecks.ProcessExecutionCheck().check(file, 0)));
  }

  @Test void packsDecodingAndRpc() throws Exception {
    String source = """
        @rpc("any_peer", "call_local")
        func hit(damage):
        \tProjectSettings.load_resource_pack(damage)
        \tvar a = bytes_to_var_with_objects(damage)
        \tvar b = Marshalls.base64_to_variant(damage, true)
        \tvar c = Marshalls.base64_to_variant(damage)
        \tvar d = bytes_to_var(damage)
        @rpc("authority")
        func sync_state():
        \tpass
        """;
    var file = parse(source);
    assertEquals(List.of(3), lines(new SecurityChecks.ResourcePackLoadingCheck().check(file, 0)));
    assertEquals(List.of(4, 5), lines(new SecurityChecks.ObjectDecodingCheck().check(file, 0)));
    var rpc = new SecurityChecks.RpcAnyPeerCheck().check(file, 0);
    assertEquals(List.of(2), lines(rpc));
    assertEquals("Make sure that \"hit\" checks the calling peer and its arguments.", rpc.get(0).message());
  }

  @Test void hardcodedCredentialsLeaveNamesValuesAndPlaceholdersOut() throws Exception {
    String source = """
        const API_KEY = "AIzaSyD3x4mpl3K3y9"
        var db_password: String = "s3cr3tP4ss"
        var config = {"client_secret": "Zx81kQ0pLm"}
        const TOKEN_URL = "Zx81kQ0pLm"
        const CLIENT_ID = "Zx81kQ0pLm"
        const TOKEN_EXPIRED = "token_expired"
        const TOKEN_SETTING = "app/client_token"
        var password = "Enter your password"
        var auth_token = "<your-token>"
        var secret = ""
        const SECRET_A = "app//client_token"
        const SECRET_B = "app/client_token/"
        """;
    var violations = new SecurityChecks.HardcodedCredentialCheck().check(parse(source), 0);
    assertEquals(List.of(1, 2, 3, 11, 12), lines(violations));
    assertTrue(violations.stream().noneMatch(v -> v.message().contains("AIza") || v.message().contains("s3cr3t")));
    assertEquals("Remove this hard-coded value of \"client_secret\"; load the credential from a secure source.", violations.get(2).message());
  }

  @Test void weakOptionsOnEngineClasses() throws Exception {
    String source = """
        func f(ctx):
        \tctx.start(HashingContext.HASH_MD5)
        \tctx.start(HashingContext.HASH_SHA256)
        \tvar aes = AESContext.MODE_ECB_ENCRYPT
        \tvar tls = TLSOptions.client_unsafe()
        \tvar safe = TLSOptions.client()
        """;
    var file = parse(source);
    assertEquals(List.of(2), lines(new SecurityChecks.WeakHashCheck().check(file, 0)));
    assertEquals(List.of(4), lines(new SecurityChecks.AesEcbReferenceCheck().check(file, 0)));
    assertEquals(List.of(5), lines(new SecurityChecks.TlsUnsafeOptionsCheck().check(file, 0)));
  }

  @Test void aDeclaredNameHidesTheEngineClass() throws Exception {
    assertTrue(new SecurityChecks.ProcessExecutionCheck().check(parse("var OS = preload(\"res://fake_os.gd\")\nfunc f():\n\tOS.execute(\"ls\", [])\n"), 0).isEmpty());
    assertEquals(RuleType.VULNERABILITY, new SecurityChecks.WeakHashCheck().type());
  }

  @Test void longLiteralsStayLinear() throws Exception {
    var shell = parse("func f():\n\tOS.execute(\"" + "x".repeat(200_000) + "\", [])\n");
    var credential = parse("var password = \"" + "<>".repeat(100_000) + "\u0085\"\n");
    assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
      assertTrue(new SecurityChecks.ShellCommandCheck().check(shell, 0).isEmpty());
      new SecurityChecks.HardcodedCredentialCheck().check(credential, 0);
    });
  }

  @Test void longPathsDoNotOverflowTheStack() throws Exception {
    var path = parse("var token = \"" + "a/".repeat(50_000) + "b\"\n");
    assertTrue(new SecurityChecks.HardcodedCredentialCheck().check(path, 0).isEmpty());
  }

  private static SourceFile parse(String source) throws Exception {
    var tokens = GDScriptLexer.lex(source);
    return new SourceFile(source, tokens, SizeMetrics.of(tokens), GDScriptParser.parse(tokens));
  }

  private static List<Integer> lines(List<Violation> violations) { return violations.stream().map(Violation::line).toList(); }
}
