package com.warrenfalk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class EcmaSpecToG4 {

	public static void main(String[] args) throws Exception {
		String specHtml;
		
		HashMap<String,String> unicodeMap = buildUnicodeMap();
		
		File cacheFile = new File("cache.html");
		if (!cacheFile.exists()) {
			System.out.println("downloading...");
			URL website = new URL("http://ecma-international.org/ecma-262/5.1/");
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			try (FileOutputStream fos = new FileOutputStream("cache.html")) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			}
		}
		System.out.println("reading...");
		specHtml = readFile(cacheFile.getPath(), Charset.forName("utf-8"));
		
		System.out.println("parsing...");
		Document doc = Jsoup.parse(specHtml);
		
		Element section = doc.select("section[id=sec-A.1]").first();
		
		Elements rules = section.select("div[class=gp]");
		
		System.out.println("Found " + rules.size() + " rules");
		
		for (Element rule : rules) {
			String name = rule.select("div.lhs > span.nt").first().text();
			assert (rule.select("div.lhs > span.geq").first().text().equals("::"));
			System.out.println(name.toUpperCase());
			Elements rhss = rule.select("div.rhs");
			for (int i = 0; i < rhss.size(); i++) {
				System.out.print(i == 0 ? "    :" : "    |");
				Element rhs = rhss.get(i);
				for (Node node : rhs.childNodes()) {
					if (node instanceof Element) {
						Element element = (Element)node;
						if ("span".equals(element.tagName()) && element.classNames().contains("gprose") && "any Unicode code unit".equals(element.text())) {
							System.out.print(" .");
						}
						else if ("span".equals(element.tagName()) && element.classNames().contains("nt")) {
							System.out.print(" " + element.text().toUpperCase());
						}
						else if ("span".equals(element.tagName()) && element.classNames().contains("grhsannot")) {
							System.out.print("  /* TODO: ");
							System.out.print(element.text());
							System.out.print(" */");
						}
						else {
							System.err.println("Unexpected element: " + element.outerHtml());
						}
					}
					else if (node instanceof TextNode) {
						TextNode tn = (TextNode)node;
						if (tn.text().startsWith("<") && tn.text().endsWith(">")) {
							// unicode character name
							String uname = tn.text().substring(1, tn.text().length() - 1);
							if ("USP".equals(uname)) {
								// special, meaning any unicode space
								System.out.print(" [\\u1680\\u2000-\\u200A\\u202F\\u205F\\u3000]");
							}
							else {
								String ucode = unicodeMap.get(uname);
								if (ucode == null)
									System.err.println("		map.put(\"" + uname + "\", \"\\u\");");
								else
									System.out.print(" " + ucode);
							}
						}
					}
					else {
						System.err.println("Unexpected node: " + node.outerHtml());
					}
				}
				System.out.println();
			}
			System.out.println("    ;");
		}
		
		System.out.println("done");
		
	}
	
	private static HashMap buildUnicodeMap() {
		HashMap<String,String> map = new HashMap<String,String>();
		map.put("TAB", "\\u0009");
		map.put("VT", "\\u000B");
		map.put("FF", "\\u000C");
		map.put("SP", "\\u0020");
		map.put("NBSP", "\\u00A0");
		map.put("BOM", "\\uFEFF");
	    map.put("LF", "\\u000A");
		map.put("CR", "\\u000D");
		map.put("LS", "\\u2028");
	    map.put("PS", "\\u2029");
		return map;
	}

	static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return encoding.decode(ByteBuffer.wrap(encoded)).toString();
	}

}
