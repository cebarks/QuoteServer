import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.WeakHashMap;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

@SuppressWarnings("unused")
public class QuoteServer extends NanoHTTPD {

    private static final File PID = new File("./run/quoteserver.pid");
    private static final File COUNTER = new File("./data/hits.counter");
    private static final Random random = new Random();
    private static QuoteServer server;

    private String INDEX_HTML = "";
    private String ADD_HTML = "";
    private String QUOTE_HTML = "";
    private String ADDDB_HTML = "";

    private final File rootDir;
    private final File dataDir;

    private Map<String, QuoteDatabase> quoteMap;
    private List<String> validAddCodes;
    private int counter;

    public QuoteServer(String host, int port, File rootDir) {
	super(host, port);

	this.rootDir = rootDir;
	this.dataDir = new File(rootDir, "data");

	dataDir.mkdirs();

	this.quoteMap = new WeakHashMap<String, QuoteDatabase>();
	validAddCodes = new ArrayList<String>();

	Runtime.getRuntime().addShutdownHook(new Thread() {
	    @Override
	    public void run() {
		QuoteServer.onShutdown();
	    }
	});

	writePID();

	try {
	    ADD_HTML = readAllLines(new File(rootDir, "add.html"));
	    ADDDB_HTML = readAllLines(new File(rootDir, "adddb.html"));
	    INDEX_HTML = readAllLines(new File(rootDir, "index.html"));
	    QUOTE_HTML = readAllLines(new File(rootDir, "quote.html"));
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    @Override
    public Response serve(IHTTPSession session) {
	String uri = session.getUri();

	if (uri.equals("/")) {
	    return getIndexResponse();
	} else if (uri.startsWith("/quote")) {
	    String name = uri.split("/")[2].toLowerCase();
	    Response r = getQuoteResponse(name);
	    return r;
	} else if (uri.equals("/add")) {
	    return getAddResponse();
	} else if (uri.equals("/adddb")) {
	    return getAddDatabaseResponse();
	} else if (uri.startsWith("/adddatabase")) {
	    Properties p = parseQuery(session.getQueryParameterString());
	    quoteMap.put(p.getProperty("name"),
		    QuoteDatabase.create(p.getProperty("name")));
	    return new Response(Status.ACCEPTED, NanoHTTPD.MIME_PLAINTEXT, "");
	} else if (uri.startsWith("/addquote")) {
	    Properties p = parseQuery(session.getQueryParameterString());
	    System.out.println(p.getProperty("quote"));
	    quoteMap.get(p.getProperty("name")).addQuote(
		    decodeUrlString(p.getProperty("quote")));
	    return new Response(Status.ACCEPTED, NanoHTTPD.MIME_PLAINTEXT, "");
	}

	return getNotFoundResponse();
    }

    @Override
    public void stop() {
	for (QuoteDatabase qd : quoteMap.values()) {
	    try {
		qd.save();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}

	try {
	    FileWriter out = new FileWriter(COUNTER);
	    out.write(counter);
	    out.close();
	} catch (IOException e) {
	    e.printStackTrace();
	}

	super.stop();
    }

    @Override
    public void start() throws IOException {
	try {
	    for (File f : dataDir.listFiles(new FilenameFilter() {
		@Override
		public boolean accept(File dir, String name) {
		    return name.endsWith(".qdb");
		}
	    })) {
		String name = ""
			+ f.getName().subSequence(0, f.getName().length() - 4);
		quoteMap.put(name, QuoteDatabase.load(name));
	    }
	} catch (ClassNotFoundException e) {
	    e.printStackTrace();
	}
	super.start();
    }

    public static void main(String[] args) {
	int port = 8383;
	String host = "127.0.0.1";
	File rootDir = new File(".");

	for (int i = 0; i < args.length; ++i) {
	    if (args[i].equalsIgnoreCase("-h")
		    || args[i].equalsIgnoreCase("--host")) {
		host = args[i + 1];
	    } else if (args[i].equalsIgnoreCase("-p")
		    || args[i].equalsIgnoreCase("--port")) {
		port = Integer.parseInt(args[i + 1]);
	    } else if (args[i].equalsIgnoreCase("-d")
		    || args[i].equalsIgnoreCase("--data-dir")) {
		rootDir = new File(args[i + 1]);
	    }
	}

	server = new QuoteServer(host, port, rootDir);

	try {
	    server.start();
	    System.out.println("Started!");
	} catch (IOException e) {
	    System.out.println("Couldn't start server: " + e.getMessage());
	}

	while (true) {
	    try {
		Thread.sleep(Long.MAX_VALUE);
		Thread.yield();
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    private void writePID() {
	try {
	    PID.createNewFile();
	    PID.deleteOnExit();

	    int pid = Integer.parseInt(ManagementFactory.getRuntimeMXBean()
		    .getName().split("@")[0]);

	    FileOutputStream fos = new FileOutputStream(PID);
	    PrintStream out = new PrintStream(fos);

	    out.print(pid);
	    out.close();
	} catch (NumberFormatException | IOException e) {
	    e.printStackTrace();
	}
    }

    protected static void onShutdown() {
	server.stop();
	System.out.println("Shutting down.");
    }

    public static QuoteServer getServer() {
	return server;
    }

    protected Response getNotFoundResponse() {
	return new Response(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
		"Error 404, file not found.");
    }

    protected Response getInternalErrorResponse(String s) {
	return new Response(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
		"INTERNAL ERRROR: " + s);
    }

    protected Response getIndexResponse() {
	String html = INDEX_HTML;

	StringBuilder sb = new StringBuilder();

	for (QuoteDatabase qd : quoteMap.values()) {
	    sb.append(String.format(
		    "<a href=\"/quote/%s\">%s</option></a></br>\n", qd
			    .getName().toLowerCase(), qd.getName()));
	}

	html = html.replace("%NAME_OPTIONS%", sb.toString().trim());
	// TODO implement
	html = html.replace("%COUNTER%", (counter++) + "");

	return new Response(Status.OK, NanoHTTPD.MIME_HTML, html);
    }

    protected Response getAddResponse() {
	String html = ADD_HTML;

	StringBuilder sb = new StringBuilder();

	for (QuoteDatabase qd : quoteMap.values()) {
	    sb.append(String.format("<option value=\"%s\">%s</option>\n", qd
		    .getName().toLowerCase(), qd.getName()));
	}

	html = html.replace("%NAME_OPTIONS%", sb.toString().trim());

	return new Response(Status.OK, NanoHTTPD.MIME_HTML, html);
    }

    private Response getAddDatabaseResponse() {
	return new Response(Status.OK, NanoHTTPD.MIME_HTML, ADDDB_HTML);
    }

    protected Response getQuoteResponse(String name) {
	String html = QUOTE_HTML;
	QuoteDatabase qd = quoteMap.get(name);
	if (qd == null)
	    return getNotFoundResponse();

	html = html.replace("%NAME%", name);
	html = html.replace("%QUOTE%", quoteMap.get(name).getRandomQuote());

	return new Response(Status.OK, NanoHTTPD.MIME_HTML + ";charset=utf-8",
		html);
    }

    public File getDataDirectory() {
	return dataDir;
    }

    public String decodeUrlString(String url) {
	String str = null;
	try {
	    System.out.println(url);
	    str = URLDecoder.decode(url, "UTF-8");
	} catch (UnsupportedEncodingException e) {
	    e.printStackTrace();
	}
	return str;
    }

    public static Properties parseQuery(String queryString) {
	Properties props = new Properties();
	if (!queryString.contains("&")) {
	    String[] ss = queryString.split("=");
	    props.setProperty(ss[0], ss[1]);
	} else {
	    for (String s : queryString.split("&")) {
		String[] ss = s.split("=");
		props.setProperty(ss[0], ss[1]);
	    }
	}
	return props;
    }

    public static String readAllLines(File f) throws IOException {
	BufferedReader in = new BufferedReader(new InputStreamReader(
		new FileInputStream(f), "UTF-8"));
	StringBuilder sb = new StringBuilder();

	String s;

	while ((s = in.readLine()) != null) {
	    sb.append(s + "\n");
	}

	in.close();

	return sb.toString();
    }

    public void addDatabase(String name, QuoteDatabase qd) {
	quoteMap.put(name.toLowerCase(), qd);
    }
}
