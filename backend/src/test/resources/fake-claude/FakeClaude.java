import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Stands in for the "claude" command in tests. Usage: FakeClaude <responseFile> <logFile> <claude args...>.
 * Writes each claude argument (one per line, then the prompt from stdin) to logFile and prints responseFile.
 */
public class FakeClaude {

	public static void main(String[] args) throws Exception {
		List<String> log = new ArrayList<>();
		for (int i = 2; i < args.length; i++) {
			log.add("ARG<" + args[i].replace("\n", "\\n") + ">");
		}
		log.add("STDIN<" + new String(System.in.readAllBytes(), StandardCharsets.UTF_8).replace("\n", "\\n") + ">");
		Files.write(Path.of(args[1]), log, StandardCharsets.UTF_8);
		System.out.print(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
	}

}
