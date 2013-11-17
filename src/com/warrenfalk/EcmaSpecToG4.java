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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;

public class EcmaSpecToG4 {
	
	private static final Pattern ucharListPattern = Pattern.compile("^(<[A-Z]+>)(\\s<[A-Z]+>)*\\s?$");
	static HashMap<String,String> unicodeMap = buildUnicodeMap();
	private static final Pattern anyUcharInCategories = Pattern.compile("[“\"](.*?) \\((.*?)\\)[”\"]");

	public static void main(String[] args) throws Exception {
		String specHtml;
		
		
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
			if ("SourceCharacter".equals(name))
				continue;
			System.out.println(name.toUpperCase());
			Elements rhss = rule.select("div.rhs");
			for (int i = 0; i < rhss.size(); i++) {
				System.out.print(i == 0 ? "    :" : "    |");
				Element rhs = rhss.get(i);
				for (Node node = rhs.childNode(0); node != null; ) {
					node = processRightHandNode(node);
				}
				System.out.println();
			}
			System.out.println("    ;");
		}
		
		System.out.println("done");
		
	}
	
	private static final Pattern uPlusCode = Pattern.compile("^U\\+([0-9A-Fa-f]+)$");
	
	private static String getUnicodeSet(String unicodeSetCode) throws Exception {
		String filename = "cache_ucode_" + unicodeSetCode + ".html";
		File cacheFile = new File(filename);
		if (!cacheFile.exists()) {
			URL website = new URL("http://www.fileformat.info/info/unicode/category/" + unicodeSetCode + "/list.htm");
			ReadableByteChannel rbc = Channels.newChannel(website.openStream());
			try (FileOutputStream fos = new FileOutputStream(filename)) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			}
		}
		String spec = readFile(cacheFile.getPath(), Charset.forName("utf-8"));
		Document doc = Jsoup.parse(spec);
		Elements codeElements = doc.select("table.table-list > tbody > tr > td:eq(0) > a");
		ArrayList<Integer> codeList = new ArrayList<Integer>();
		for (Element code : codeElements) {
			Matcher m = uPlusCode.matcher(code.text());
			if (m.find()) {
				if (m.group(1).length() > 4)  // don't think 5-digit unicode is supported by Java strings
					continue;
				int codeVal = Integer.parseInt(m.group(1), 16);
				codeList.add(codeVal);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < codeList.size(); i++) {
			int code = codeList.get(i);
			int j;
			for (j = i + 1; j < codeList.size() && codeList.get(j) == (codeList.get(j - 1) + 1); j++) ;
			j--;
			sb.append("\\\\u");
			sb.append(String.format("%04x", code));
			if (j > i) {
				sb.append('-');
				sb.append("\\\\u");
				sb.append(String.format("%04x", codeList.get(j)));
				i = j;
			}
		}
		return sb.toString();
	}
	
	private static Node processRightHandNode(Node node) throws Exception {
		if (node instanceof Element) {
			Element element = (Element)node;
			if ("span".equals(element.tagName()) && element.classNames().contains("gprose") && "any Unicode code unit".equals(element.text())) {
				System.out.print(" .");
			}
			else if ("span".equals(element.tagName()) && element.classNames().contains("gprose") && element.text() != null && element.text().startsWith("any character in the Unicode categor")) {
				Matcher m = anyUcharInCategories.matcher(element.text());
				System.out.print(" [");
				while (m.find()) {
					String category = m.group(2);
					String set = getUnicodeSet(category);
					System.out.print(set);
				}
				System.out.println("]");
			}
			else if ("span".equals(element.tagName()) && element.classNames().contains("grhsmod") && "or".equals(element.text())) {
				System.out.print(" |");
			}
			else if ("span".equals(element.tagName()) && element.classNames().contains("nt")) {
				String ref = element.text();
				Element next = element.nextElementSibling();
				if ("SourceCharacter".equals(ref) && next != null && "span".equals(next.tagName()) && next.classNames().contains("grhsmod") && "but not one of".equals(next.text())) {
					System.out.print(" ~(");
					next = next.nextElementSibling();
					while (next != null) {
						processRightHandNode(next);
						next = next.nextElementSibling();
					}
					System.out.print(" )");
					return next;
				}
				if ("SourceCharacter".equals(ref) && next != null && "span".equals(next.tagName()) && next.classNames().contains("grhsmod") && "but not".equals(next.text())) {
					System.out.print(" ~(");
					next = next.nextElementSibling();
					while (next != null) {
						processRightHandNode(next);
						next = next.nextElementSibling();
					}
					System.out.print(" )");
					return next;
				}
				else if ("SourceCharacter".equals(ref))
					System.out.print(" .");
				else
					System.out.print(" " + element.text().toUpperCase());
			}
			else if ("span".equals(element.tagName()) && element.classNames().contains("grhsannot")) {
				System.out.print("  /* TODO: ");
				System.out.print(element.text());
				System.out.print(" */");
			}
			else if ("code".equals(element.tagName()) && element.classNames().contains("t")) {
				System.out.print(" '" + element.text().replaceAll("\\\\", "\\\\\\\\").replaceAll("'", "\\\\'") + "'");
			}
			else if ("sub".equals(element.tagName()) && "opt".equals(element.text())) {
				System.out.print('?');
			}
			else {
				System.err.println("Unexpected element: " + element.outerHtml());
			}
			
			Element next = element.nextElementSibling();
			if (next != null) {
				if ("span".equals(next.tagName()) && next.classNames().contains("grhsmod") && "but not one of".equals(next.text())) {
					System.out.print(" /* TODO: but not one of: ");
					next = next.nextElementSibling();
					while (next != null) {
						processRightHandNode(next);
						next = next.nextElementSibling();
					}
					System.out.print(" */");
					return next;
				}
				else if ("span".equals(next.tagName()) && next.classNames().contains("grhsmod") && "but not".equals(next.text())) {
					System.out.print(" /* TODO: but not: ");
					next = next.nextElementSibling();
					while (next != null) {
						processRightHandNode(next);
						next = next.nextElementSibling();
					}
					System.out.print(" */");
					return next;
				}
			}
		}
		else if (node instanceof TextNode) {
			TextNode tn = (TextNode)node;
			Matcher lm = ucharListPattern.matcher(tn.text());
			if (lm.matches()) {
				processUchar(lm.group(1));
				for (int g = 2; g <= lm.groupCount(); g++) {
					if (lm.group(g) != null)
						processUchar(lm.group(g).trim());
				}
			}
			else if (tn.text().trim().length() == 0) {
				// ignore
			}
			else {
				System.err.println("Unexpected text node: " + tn.text());
			}
		}
		else {
			System.err.println("Unexpected node: " + node.outerHtml());
		}
		return node.nextSibling();
	}

	private static void processUchar(String spec) {
		// unicode character name
		String uname = spec.substring(1, spec.length() - 1);
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
	
	private static HashMap<String,String> buildUnicodeMap() {
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
	    map.put("ZWNJ", "\\u200C");
	    map.put("ZWJ", "\\u200D");
		return map;
	}

	static String readFile(String path, Charset encoding) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return encoding.decode(ByteBuffer.wrap(encoded)).toString();
	}

}
