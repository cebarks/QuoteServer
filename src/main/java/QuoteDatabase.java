import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuoteDatabase {
    private static final Random random = new Random();

    private final List<String> quotes;
    private final String name;

    private QuoteDatabase(String name, List<String> quotes) {
	this.quotes = quotes;
	this.name = name.toLowerCase();
    }

    public String getName() {
	return name;
    }

    public String getQuote(int index) {
	return quotes.get(index);
    }

    public String getRandomQuote() {
	return getQuote(random.nextInt(getDatabaseSize()));
    }

    public int getDatabaseSize() {
	return quotes.size();
    }

    public void addQuote(String quote) {
	if (quotes.contains(quote))
	    return;
	quotes.add(quote);
	try {
	    save();
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public static QuoteDatabase create(String name) {
	return create(name, new ArrayList<String>());
    }

    public static QuoteDatabase create(String name, List<String> list) {
	return new QuoteDatabase(name, list);
    }

    public static QuoteDatabase load(String name) throws FileNotFoundException,
	    IOException, ClassNotFoundException {
	return create(
		name,
		toList(QuoteServer.readAllLines(
			new File(QuoteServer.getServer().getDataDirectory(),
				name + ".qdb")).split("\n")));
    }

    public void save() throws FileNotFoundException, IOException {
	PrintStream out = new PrintStream(new File(QuoteServer.getServer()
		.getDataDirectory(), name + ".qdb"), "UTF-8");
	for (String s : quotes)
	    out.println(s);
	out.close();
    }

    public static List<String> toList(String[] strings) {
	ArrayList<String> strlist = new ArrayList<String>();
	for (String s : strings) {
	    strlist.add(s);
	}
	return strlist;
    }
}
