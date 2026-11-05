package org.orebron.gdscript.checks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.orebron.gdscript.checks.Calls.Call;
import org.orebron.gdscript.lexer.Token;
import org.orebron.gdscript.lexer.TokenKind;
import org.sonar.api.rules.RuleType;
import org.sonar.api.server.rule.RulesDefinition.OwaspTop10;

/**
 * The security plan's L1 review rules: security-sensitive engine calls that a person must judge. Each is a
 * vulnerability with a LOW (Minor) or MEDIUM (Major) SECURITY impact; the CWE names the weakness to rule out.
 */
final class SecurityChecks {
  private SecurityChecks() {}

  abstract static class SecurityCheck implements Check {
    @Override public RuleType type() { return RuleType.VULNERABILITY; }
    @Override public List<String> tags() { return List.of("security"); }

    abstract List<Violation> find(Calls calls, SourceFile file);

    @Override public final List<Violation> check(SourceFile file, int unused) {
      Calls calls = Calls.of(file);
      return calls == null ? List.of() : find(calls, file);
    }

    static String review(String risk, String questions, String fix) {
      return "<p>" + risk + "</p><h2>Ask Yourself Whether</h2><ul>" + questions + "</ul><h2>Recommended Secure Coding Practices</h2><p>"
          + fix + "</p>";
    }
  }

  static final class ProcessExecutionCheck extends SecurityCheck {
    static final Set<String> CALLS = Set.of("execute", "create_process", "execute_with_pipe");

    @Override public String key() { return "process-execution"; }
    @Override public String name() { return "Starting external processes is security-sensitive"; }
    @Override public String severity() { return "MINOR"; }

    @Override public String htmlDescription() {
      return review("<code>OS.execute</code>, <code>OS.create_process</code> and <code>OS.execute_with_pipe</code> run "
          + "programs of the player's system with the game's rights.",
          "<li>Can a player, a mod, a save file or the network influence the program or its arguments?</li>"
          + "<li>Is the program found through <code>PATH</code>, where another program could take its place?</li>",
          "Run only fixed programs by absolute path, pass arguments as separate array elements, and never build "
          + "them from untrusted data.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      return calls.on("OS", CALLS).stream()
          .map(call -> call.violation(this, "Make sure that starting the process \"" + call.member().text() + "\" runs is safe here."))
          .toList();
    }
  }

  /** A shell or interpreter run in its command-string form with a command that is not a constant. */
  static final class ShellCommandCheck extends SecurityCheck {
    private static final Map<String, Set<String>> COMMAND_FLAGS = Map.ofEntries(
        Map.entry("sh", Set.of("-c")), Map.entry("bash", Set.of("-c")), Map.entry("zsh", Set.of("-c")),
        Map.entry("dash", Set.of("-c")), Map.entry("ksh", Set.of("-c")), Map.entry("fish", Set.of("-c")),
        Map.entry("cmd", Set.of("/c", "/k")), Map.entry("powershell", Set.of("-command", "-c", "-encodedcommand")),
        Map.entry("pwsh", Set.of("-command", "-c", "-encodedcommand")), Map.entry("python", Set.of("-c")),
        Map.entry("python3", Set.of("-c")), Map.entry("node", Set.of("-e", "--eval")), Map.entry("perl", Set.of("-e")),
        Map.entry("ruby", Set.of("-e")), Map.entry("php", Set.of("-r")));

    @Override public String key() { return "shell-command"; }
    @Override public String name() { return "Running shell commands built at run time is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(78); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A3); }

    @Override public String htmlDescription() {
      return review("Passing a command string that is not a constant to a shell or interpreter, as in "
          + "<code>OS.execute(\"sh\", [\"-c\", command])</code>, lets anyone who controls part of that string run "
          + "any command.",
          "<li>Can any part of the command come from a player, a mod, a save file or the network?</li>",
          "Run the program directly with each argument as its own array element instead of going through a shell, "
          + "or allow only fixed commands.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      var violations = new ArrayList<Violation>();
      for (Call call : calls.on("OS", ProcessExecutionCheck.CALLS)) {
        String program = Calls.stringValue(call.argument(0));
        List<List<Token>> arguments = Calls.arrayElements(call.argument(1));
        if (program == null || arguments == null) continue;
        // lastIndexOf, not replaceFirst(".*/"): that regex retries from every position, quadratic on a long literal
        String path = program.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) name = name.substring(0, name.length() - 4);
        Set<String> flags = COMMAND_FLAGS.get(name);
        if (flags == null) continue;
        for (int i = 0; i + 1 < arguments.size(); i++) {
          String flag = Calls.stringValue(arguments.get(i));
          if (flag != null && flags.contains(flag.toLowerCase(Locale.ROOT)) && !calls.isConstant(arguments.get(i + 1))) {
            violations.add(call.violation(this, "Make sure that the command passed to \"" + name + "\" cannot be controlled by a player or the network."));
            break;
          }
        }
      }
      return violations;
    }
  }

  static final class ShellOpenCheck extends SecurityCheck {
    @Override public String key() { return "shell-open-dynamic"; }
    @Override public String name() { return "Opening URIs and files built at run time is security-sensitive"; }
    @Override public String severity() { return "MINOR"; }

    @Override public String htmlDescription() {
      return review("<code>OS.shell_open</code> hands its argument to the system, which opens it with whatever "
          + "program is registered: a web page, a file or another program.",
          "<li>Can the URI or path come from a player, a mod, a save file or the network?</li>"
          + "<li>Could it use a scheme other than <code>https</code>, or point to an executable file?</li>",
          "Open only fixed URIs, or check the scheme and host against an allow-list first.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      return calls.on("OS", Set.of("shell_open")).stream().filter(call -> !calls.isConstant(call.argument(0)))
          .map(call -> call.violation(this, "Make sure that the URI opened here cannot be controlled by a player or the network."))
          .toList();
    }
  }

  static final class JavaScriptEvalCheck extends SecurityCheck {
    @Override public String key() { return "javascript-eval"; }
    @Override public String name() { return "Evaluating JavaScript built at run time is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(95); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A3); }

    @Override public String htmlDescription() {
      return review("In a Web export, <code>JavaScriptBridge.eval</code> runs its argument as JavaScript in the page, "
          + "with access to everything the page can reach.",
          "<li>Can any part of the code come from a player, the URL, a save file or the network?</li>",
          "Evaluate only constant code, and pass data through <code>JavaScriptBridge.get_interface</code> and "
          + "callbacks instead of building code strings.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      return calls.on("JavaScriptBridge", Set.of("eval")).stream().filter(call -> !calls.isConstant(call.argument(0)))
          .map(call -> call.violation(this, "Make sure that the JavaScript evaluated here cannot be controlled by a player or the network."))
          .toList();
    }
  }

  static final class ResourcePackLoadingCheck extends SecurityCheck {
    @Override public String key() { return "resource-pack-loading"; }
    @Override public String name() { return "Loading resource packs is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(829); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A8); }

    @Override public String htmlDescription() {
      return review("<code>ProjectSettings.load_resource_pack</code> adds the pack's files to <code>res://</code>, "
          + "scripts included, and can replace the game's own files; the scripts then run with the game's rights.",
          "<li>Can the pack come from a download, a mod folder or another place a player or attacker can write to?</li>"
          + "<li>Is the pack's origin verified, for example with a signature, before it is loaded?</li>",
          "Load only packs shipped with the game or verified against a trusted signature, and load them with "
          + "<code>replace_files</code> set to <code>false</code>.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      return calls.on("ProjectSettings", Set.of("load_resource_pack")).stream()
          .map(call -> call.violation(this, "Make sure that this resource pack comes from a trusted, verified source."))
          .toList();
    }
  }

  static final class ObjectDecodingCheck extends SecurityCheck {
    @Override public String key() { return "object-decoding"; }
    @Override public String name() { return "Decoding objects from data is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(502); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A8); }

    @Override public String htmlDescription() {
      return review("<code>bytes_to_var_with_objects</code> and <code>Marshalls.base64_to_variant(data, true)</code> "
          + "can create objects with scripts from the data; Godot's documentation warns that this can run code from "
          + "whoever produced the data.",
          "<li>Can the data come from a save file a player can edit, a mod, or the network?</li>",
          "Decode untrusted data without objects (<code>bytes_to_var</code>, <code>base64_to_variant(data)</code>, "
          + "JSON), and build objects from plain values yourself.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      var violations = new ArrayList<Violation>();
      calls.global("bytes_to_var_with_objects")
          .forEach(call -> violations.add(call.violation(this, "Make sure that the data decoded with objects here is trusted.")));
      calls.on("Marshalls", Set.of("base64_to_variant")).stream()
          .filter(call -> call.argument(1).size() == 1 && TokenRanges.is(call.argument(1).get(0), "true"))
          .forEach(call -> violations.add(call.violation(this, "Make sure that the data decoded with objects here is trusted.")));
      return violations;
    }
  }

  /** {@code @rpc("any_peer")} functions, which any connected peer can call. */
  static final class RpcAnyPeerCheck extends SecurityCheck {
    @Override public String key() { return "rpc-any-peer"; }
    @Override public String name() { return "Remote procedures callable by any peer are security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(862); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A1); }

    @Override public String htmlDescription() {
      return review("A function annotated with <code>@rpc(\"any_peer\")</code> can be called by every connected peer, "
          + "with any arguments, including a modified client.",
          "<li>Does the function check <code>multiplayer.get_remote_sender_id()</code> before it acts?</li>"
          + "<li>Does it validate every argument before changing game state, spending currency or granting items?</li>",
          "Use <code>\"authority\"</code> where only the server should call, and check the sender and arguments in "
          + "every <code>any_peer</code> function.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      var violations = new ArrayList<Violation>();
      List<Token> tokens = TokenRanges.significant(file.tokens());
      for (int i = 0; i < tokens.size(); i++) {
        if (tokens.get(i).kind() != TokenKind.ANNOTATION || !tokens.get(i).text().equals("@rpc")) continue;
        boolean anyPeer = false;
        int j = i + 1;
        if (j < tokens.size() && TokenRanges.is(tokens.get(j), "(")) {
          for (int depth = 0; j < tokens.size(); j++) {
            Token token = tokens.get(j);
            if (TokenRanges.is(token, "(")) depth++;
            else if (TokenRanges.is(token, ")") && --depth == 0) break;
            else if ("any_peer".equals(Calls.stringValue(List.of(token)))) anyPeer = true;
          }
        }
        for (int k = j; anyPeer && k + 1 < tokens.size(); k++) {
          if (TokenRanges.is(tokens.get(k), "func")) {
            Token name = tokens.get(k + 1);
            violations.add(Violation.at(this, name, "Make sure that \"" + name.text() + "\" checks the calling peer and its arguments."));
            break;
          }
        }
      }
      return violations;
    }
  }

  /**
   * A non-empty, non-placeholder string assigned to a credential name. Values with spaces, identifier-like values,
   * paths and URLs, names of settings, and public identifiers such as {@code *_id} and {@code public_key} are left out.
   */
  static final class HardcodedCredentialCheck extends SecurityCheck {
    private static final Pattern WORD = Pattern.compile("[A-Z]?[a-z0-9]+|[A-Z0-9]+(?![a-z])");
    private static final Set<String> SECRETS = Set.of("password", "passwd", "secret", "token", "apikey");
    private static final Set<String> NOT_SECRETS = Set.of("id", "url", "uri", "path", "file", "name", "label", "hint",
        "text", "message", "field", "length", "count", "type", "header", "prompt", "error", "format", "regex", "setting",
        "settings", "property", "option", "env");
    /** Used with {@code find()}: a {@code .*X.*} pattern backtracks quadratically when the value holds a U+0085. */
    private static final Pattern PLACEHOLDER = Pattern.compile(
        "(?i)your|example|placeholder|changeme|change_me|dummy|sample|todo|xxx|\\*\\*\\*|<|>|\\{|\\}|\\$|%");

    @Override public String key() { return "hardcoded-credential"; }
    @Override public String name() { return "Credentials should not be hard-coded"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(798); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A7); }

    @Override public String htmlDescription() {
      return review("A password, secret, token or private key written in a script ships inside every export of the "
          + "game, where anyone can extract it from the <code>.pck</code> file.",
          "<li>Is the value a real credential rather than a test value or a public identifier?</li>"
          + "<li>Would anyone who has it gain access to a server, a store account or player data?</li>",
          "Keep secrets on a server and give clients short-lived tokens; never ship long-lived credentials in the game.");
    }

    @Override List<Violation> find(Calls calls, SourceFile file) {
      var violations = new ArrayList<Violation>();
      List<Token> tokens = TokenRanges.significant(file.tokens());
      for (int i = 2; i < tokens.size(); i++) {
        String value = Calls.stringValue(List.of(tokens.get(i)));
        if (value == null || !looksSecret(value)) continue;
        Token name = assignedName(tokens, i);
        if (name != null && isCredentialName(name)) {
          violations.add(Violation.at(this, tokens.get(i).line() == tokens.get(i).endLine() ? tokens.get(i) : name,
              "Remove this hard-coded value of \"" + nameText(name) + "\"; load the credential from a secure source."));
        }
      }
      return violations;
    }

    /** The name a string is assigned to: {@code name = "..."}, {@code var name: T = "..."} or a dictionary key. */
    private static Token assignedName(List<Token> tokens, int value) {
      Token before = tokens.get(value - 1);
      if (TokenRanges.is(before, "=") || TokenRanges.is(before, ":=")) {
        Token target = tokens.get(value - 2);
        if (value >= 4 && TokenRanges.is(tokens.get(value - 3), ":")) target = tokens.get(value - 4);
        return target.kind() == TokenKind.IDENTIFIER ? target : null;
      }
      if (TokenRanges.is(before, ":") && tokens.get(value - 2).kind() == TokenKind.STRING) return tokens.get(value - 2);
      return null;
    }

    private static String nameText(Token name) {
      return name.kind() == TokenKind.STRING ? Calls.stringValue(List.of(name)) : name.text();
    }

    private static boolean isCredentialName(Token name) {
      String text = nameText(name);
      if (text == null) return false;
      var words = new ArrayList<String>();
      var matcher = WORD.matcher(text);
      while (matcher.find()) words.add(matcher.group().toLowerCase(Locale.ROOT));
      if (words.isEmpty() || NOT_SECRETS.contains(words.get(words.size() - 1))) return false;
      for (int i = 0; i < words.size(); i++) {
        if (SECRETS.contains(words.get(i))) return true;
        boolean key = i + 1 < words.size() && words.get(i + 1).equals("key");
        if (key && (words.get(i).equals("api") || words.get(i).equals("private") || words.get(i).equals("secret"))) return true;
      }
      return false;
    }

    private static boolean looksSecret(String value) {
      if (value.length() < 6 || value.chars().anyMatch(Character::isWhitespace)) return false;
      if (value.matches("[a-z_.]+|[A-Z_.]+") || value.contains("://") || value.startsWith("/")) return false;
      if (value.matches("[A-Za-z0-9_.]+(/[A-Za-z0-9_.]+)+")) return false; // a settings or resource path such as "app/client_token"
      if (value.chars().distinct().count() <= 2) return false;
      return !PLACEHOLDER.matcher(value).find();
    }
  }

  /** References to an engine constant or call that selects a weak or unsafe option. */
  abstract static class ConstantReferenceCheck extends SecurityCheck {
    abstract String receiver();
    abstract Set<String> members();
    abstract String message(Call call);

    @Override List<Violation> find(Calls calls, SourceFile file) {
      return calls.on(receiver(), members()).stream().map(call -> call.violation(this, message(call))).toList();
    }
  }

  static final class WeakHashCheck extends ConstantReferenceCheck {
    @Override public String key() { return "weak-hash"; }
    @Override public String name() { return "Using weak hashing algorithms is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(328); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A2); }
    @Override String receiver() { return "HashingContext"; }
    @Override Set<String> members() { return Set.of("HASH_MD5", "HASH_SHA1"); }
    @Override String message(Call call) { return "Make sure that " + call.member().text().substring(5) + " is not used where collisions matter; prefer SHA-256."; }

    @Override public String htmlDescription() {
      return review("MD5 and SHA-1 collisions can be computed, so they do not protect integrity, signatures or passwords.",
          "<li>Is the hash used for security, such as checking a download, a save file or a password?</li>",
          "Use <code>HashingContext.HASH_SHA256</code>, and a slow password hash on a server for passwords.");
    }
  }

  static final class AesEcbReferenceCheck extends ConstantReferenceCheck {
    @Override public String key() { return "aes-ecb-reference"; }
    @Override public String name() { return "Using AES in ECB mode is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(327); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A2); }
    @Override String receiver() { return "AESContext"; }
    @Override Set<String> members() { return Set.of("MODE_ECB_ENCRYPT", "MODE_ECB_DECRYPT"); }
    @Override String message(Call call) { return "Make sure that ECB mode is not used to protect data; prefer CBC with a random IV and a MAC."; }

    @Override public String htmlDescription() {
      return review("AES in ECB mode encrypts equal blocks to equal output, so patterns in the data stay visible and "
          + "blocks can be swapped unnoticed.",
          "<li>Is the encrypted data longer than one block, or could an attacker change it?</li>",
          "Use <code>MODE_CBC_ENCRYPT</code> with a random IV per message, and authenticate the result.");
    }
  }

  static final class TlsUnsafeOptionsCheck extends ConstantReferenceCheck {
    @Override public String key() { return "tls-unsafe-options"; }
    @Override public String name() { return "Disabling TLS certificate validation is security-sensitive"; }
    @Override public String severity() { return "MAJOR"; }
    @Override public List<Integer> cwe() { return List.of(295); }
    @Override public List<OwaspTop10> owasp2021() { return List.of(OwaspTop10.A7); }
    @Override String receiver() { return "TLSOptions"; }
    @Override Set<String> members() { return Set.of("client_unsafe"); }
    @Override String message(Call call) { return "Make sure that disabling certificate validation is not used outside development."; }

    @Override public String htmlDescription() {
      return review("<code>TLSOptions.client_unsafe()</code> makes certificate validation optional, so anyone on the "
          + "network path can impersonate the server and read or change the traffic.",
          "<li>Can this code run in a release build?</li>",
          "Use <code>TLSOptions.client()</code>, with a trusted certificate chain for self-signed development servers.");
    }
  }
}
