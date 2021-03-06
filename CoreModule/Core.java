import TurkishDictParser.Word;


import java.io.*;
import java.util.*;

/**
 * Created by Atakan Arıkan on 22.04.2016.
 */
@SuppressWarnings("Duplicates")
public class Core {
    public static ArrayList<YapımEki> yapımEkleri;
    public static ArrayList<CekimEki> cekimEkleri;
    public static HashSet<Word> turkishRoots;
    public static HashSet<Word> turkish;
    public static ArrayList<String> cekimEkleriStr = new ArrayList<>();
    public static ArrayList<String> yapimEkleriStr = new ArrayList<>();
    public static HashMap<Integer, String> alphabet;
    public static HashMap<Double, Double> lengthProbabilities;

    public static void main(String[] args) throws IOException {
        train();
        Scanner scanner = new Scanner(System.in, "UTF-8");
        String input = "";
        while (!input.equals("q")) {
            System.out.println("Enter your input: (q for exit)");
            input = scanner.next().toLowerCase();
            if (input.equals("q")) {
                System.out.println("Goodbye!");
                System.exit(0);
            }
            System.out.println("Spell correction? (y/n)");
            String spellCorr = scanner.next().toLowerCase();
            System.out.println("1. Stemmer\n2. Morphological Analyzer");
            String stemmer = scanner.next().toLowerCase();
            HashMap<String, String> results = interactUser(input, spellCorr.equals("y"), stemmer.equals("1"));
            System.out.println(results.get("normal"));
            System.out.println("\n\n");
        }
    }
    public static HashMap<String, String> interactUser(String input, boolean isEditdistance, boolean stemmer) {
        HashMap<String, String> result = new HashMap<>();
        String outputString = "";
        String word = input;
        if (isEditdistance) {
            word = Main.correctMisspelling(input);
            if (!word.equals(input)) {
                outputString += (input + " is corrected as: " + word + "\n");
            }
        }

        List<String> predictedRoots = testWords(word, stemmer);
        String predictedRoot = predictedRoots.get(0);
        double maxProb = -1;
        for (String res : predictedRoots) {
            double ratio = (double) word.length() / (double) res.length();
            ratio = (double) Math.round(ratio * 10) / 10.0;
            if (lengthProbabilities.containsKey(ratio)) {
                double prob = lengthProbabilities.get(ratio);
                if (maxProb < prob) {
                    predictedRoot = res;
                    maxProb = prob;
                }
            }
        }
        String pretty = outputString;
        String normal = outputString;
        if (!stemmer) {
            pretty += ("The system found the following possible morphological parsings: \n");
            normal += ("The system found the following possible morphological parsings: \n");
            for (String root : predictedRoots) {
                HashSet<WordDetail> possibleMorphs = morphologycallyAnalyze(root, word.substring(root.length()));
                List<PrettyWordDetail> prettyOutput = makePretty(possibleMorphs);
                for (PrettyWordDetail pwd: prettyOutput) {
                    pretty +="> " +  pwd.toString() + "\n";
                }
                for (WordDetail pwd: possibleMorphs) {
                    normal +="> " +  pwd.toString() + "\n";
                }
            }
        } else {
            pretty += ("The system found the followings as possible stems: " + predictedRoots.toString() + "\n");
            normal += ("The system found the followings as possible stems: " + predictedRoots.toString() + "\n");
            pretty += ("Among those the most possible stem is: " + predictedRoot + "\n");
            double total = 0;
            for (String predicted : predictedRoots) {
                double ratio = (double) word.length() / (double) predicted.length();
                ratio = (double) Math.round(ratio * 10) / 10.0;
                if (lengthProbabilities.containsKey(ratio)) {
                    total += lengthProbabilities.get(ratio);
                }
            }
            for (String predicted : predictedRoots) {
                if (total == 0.0) {
                    normal += "> " + predicted + " with a score of: " + 100.0 +"%\n";
                } else {
                    double ratio = (double) word.length() / (double) predicted.length();
                    ratio = (double) Math.round(ratio * 10) / 10.0;
                    if (lengthProbabilities.containsKey(ratio)) {
                        double prob = lengthProbabilities.get(ratio);
                        prob = (prob/total)*100.0;
                        prob = (double) Math.round(prob * 10) / 10.0;
                        normal += "> " + predicted + " with a score of: " + prob +"%\n";
                    } else {
                        normal += "> " + predicted + " with a score of: " + 0 +"%\n";
                    }
                }
            }
        }
        pretty += ("==============================================================================================" + "\n");
        normal += ("==============================================================================================" + "\n");
        result.put("normal", normal);
        result.put("pretty", pretty);

        return result;

    }

    private static List<PrettyWordDetail> makePretty(HashSet<WordDetail>  predictedRoots) {
        List<PrettyWordDetail> result = new ArrayList<>();
        HashSet<PrettyWordDetail> resultUnique = new HashSet<>();
        for (WordDetail wd : predictedRoots) {
            ArrayList<String> ekler = new ArrayList<>();
            for (Ek ek : wd.getEkler()) {
                ekler.add(ek.getEk() + "(" + ek.getClass().toString().substring(ek.getClass().toString().indexOf(" ") + 1) + ")");
            }
            resultUnique.add(new PrettyWordDetail(wd.getRoot(), ekler));
        }
        result.addAll(resultUnique);
        return result;
    }


    public static void train() throws IOException {
        yapımEkleri = new ArrayList<>();
        cekimEkleri = new ArrayList<>();
        turkish = new HashSet<>();
        turkishRoots = new HashSet<>();
        alphabet = new HashMap<>();
        lengthProbabilities = new HashMap<>();
        createAlphabet();
        readTurkish();
        readSuffixes("yapımekleri.txt", "çekimekleri.txt", "TurkishRoots.txt");
        readMetuBankAndProcess("turkish_metu_sabanci_train.conll", true);
   //     readMetuBankAndProcess("turkish_metu_sabanci_train.conll", false);
        Main.readFromFile();
        System.out.println();
    }

    /**
     * Tests whether given string can be casted into Double.
     *
     * @param str String to be tested
     * @return true if the string is castable to Double, false otherwise.
     */
    public static boolean isDouble(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException nfe) {
        }
        return false;
    }

    /**
     * takes the input, makes all lowercased.
     * gets rid of the words containing all nonword characters.
     * gets rid of the nonword characters at the beginning of a word.
     * gets rid of the nonword characters at the end of a word.
     * gets rid of any nonword character in a word (excluding digits) // 19.2 or 16,3 will pass, whereas "It's okay" will be "its okay"
     */
    public static String tokenizeString(String potentialToken) {
        StringBuilder stringBuilder = new StringBuilder();
        if (potentialToken.length() > 0) { // parseDouble thinks "1980." a double. avoid that.
            if (!Character.isDigit(potentialToken.charAt(potentialToken.length() - 1)) && !Character.isLetter(potentialToken.charAt(potentialToken.length() - 1))) {
                potentialToken = potentialToken.substring(0, potentialToken.length() - 1);
            }
        }
        if (isDouble(potentialToken) && isDouble(potentialToken.replace(",", "."))) {

            return potentialToken;
        } else {
            for (int j = 0; j < potentialToken.length(); j++) {
                if (Character.isDigit(potentialToken.charAt(j)) || Character.isLetter(potentialToken.charAt(j))) {

                    stringBuilder.append(potentialToken.charAt(j));
                }
            }


        }
        return stringBuilder.toString();
    }

    /**
     * Reads the MetuBank dataset and either tests the Stemmer module or calculates word length probablities depending on calculateRates parameter.
     * <p>
     * To calculate the rates reads every word and their annotated stem. Compares their length and increases the number of times we see this word length-stem length ratio.
     * After calculating the counts, normalizes each value for word length-stem length ratio by t he number of elements we saw in total to calculate the probability of
     * seeing a stem of length x after a word of length y.
     * <p>
     * If the calculateRates parameter is set to false, the code will read the MetuBank and test our Stemmer for every annotated word-stem tuple. Calculates accuracy for the whole data.
     *
     * @param filepath       /path/to/metubank_train.conll
     * @param calculateRates true means only calculate the wordlength-stemlength probabilities, false means test our Stemmer.
     * @throws IOException
     */
    private static void readMetuBankAndProcess(String filepath, boolean calculateRates) throws IOException {
        BufferedReader read;
        String str;
        InputStream bytes = Core.class.getClassLoader().getResourceAsStream(filepath);
        Reader chars = new InputStreamReader(bytes);
        read = new BufferedReader(chars);
        int cnt = 0;
        int correct = 0;
        TreeMap<Double, Integer> rates = new TreeMap<>();
        while ((str = read.readLine()) != null) {
            if (str.contains("\t")) {
                String[] tokens = str.split("\t");

                if (tokens.length > 2 && !tokens[1].contains("_") && !tokens[2].contains("_") && !tokens[3].toLowerCase().equals("punc") && tokens[1].length() < 15) {
                    String word = tokens[1];
                    String root = tokens[2];


                    if (!calculateRates) {
                        cnt++;

                      //  System.out.print("Results for " + tokens[1].toLowerCase() + " (" + tokens[2] + ") : ");
                        ArrayList<String> predictedRoots = testWords(tokenizeString(tokens[1].toLowerCase()), false);
                        if (predictedRoots.size() > 0) {
                            double maxProb = -1;
                            String predictedRoot = predictedRoots.get(0);
                            for (String res : predictedRoots) {
                                HashSet<WordDetail> hahaha = morphologycallyAnalyze(res, word.substring(res.length()));
                                if (hahaha.size() == 1) {
                                    System.out.println(tokens[1].toLowerCase() +": " +hahaha.toString());
                                }
                                double ratio = (double) word.length() / (double) res.length();
                                ratio = (double) Math.round(ratio * 10) / 10.0;
                                if (lengthProbabilities.containsKey(ratio)) {
                                    double prob = lengthProbabilities.get(ratio);
                                    if (maxProb < prob) {
                                        predictedRoot = res;
                                        maxProb = prob;
                                    }
                                }
                            }
                            if (predictedRoot.toLowerCase().equals(root.toLowerCase())) {
                                correct++;
                            }
                        }
                        if (predictedRoots.size() > 2) {
                         //   System.out.println(tokens[1].toLowerCase() +": " + predictedRoots.toString() + " " + correct + "/" + cnt);
                        }
                    } else {
                        double ratio = (double) word.length() / (double) root.length();
                        ratio = (double) Math.round(ratio * 10) / 10.0;
                        if (rates.containsKey(ratio)) {
                            rates.put(ratio, rates.get(ratio) + 1);
                        } else {
                            rates.put(ratio, 1);
                        }
                    }

                }
            }
        }

        if (calculateRates) {
            for (double ratio : rates.keySet()) {
                int score = rates.get(ratio);
                lengthProbabilities.put(ratio, (double) score / 29081.0);
            }
        } else {
            System.out.println(correct + "/" + cnt + " = " + 100 * correct / cnt + "%");
        }

    }

    /**
     * This function indexes the Turkish Alphabet and assigns an index to each letter.
     */
    public static void createAlphabet() {
        alphabet.put(0, "a");
        alphabet.put(1, "b");
        alphabet.put(2, "c");
        alphabet.put(3, "cx");
        alphabet.put(4, "d");
        alphabet.put(5, "e");
        alphabet.put(6, "f");
        alphabet.put(7, "g");
        alphabet.put(8, "h");
        alphabet.put(9, "ix");
        alphabet.put(10, "i");
        alphabet.put(11, "j");
        alphabet.put(12, "k");
        alphabet.put(13, "l");
        alphabet.put(14, "m");
        alphabet.put(15, "n");
        alphabet.put(16, "o");
        alphabet.put(17, "ox");
        alphabet.put(18, "p");
        alphabet.put(19, "r");
        alphabet.put(20, "s");
        alphabet.put(21, "sx");
        alphabet.put(22, "t");
        alphabet.put(23, "u");
        alphabet.put(24, "ux");
        alphabet.put(25, "v");
        alphabet.put(26, "y");
        alphabet.put(27, "z");
    }


    public static HashSet<WordDetail> morphologycallyAnalyze(String root, String ekler) {
        HashSet<WordDetail> results = new HashSet<>();
        Integer length = ekler.length();

        // for the combination of Yapım + Çekim ekleri
        for (int i = 1; i < length; i++) {
            String candidateYapımEkleri = ekler.substring(0, i);
            ArrayList<String> answer = allProducable(candidateYapımEkleri, yapimEkleriStr);
            ArrayList<WordDetail> filtered = new ArrayList<>();

            if (answer != null) {
                ArrayList<WordDetail> wds = markSuffixes(answer, root, true, false);
                filtered = filter(wds, true, "");
                if (filtered.size() > 0) {
                    String candidateCekimEkleri = ekler.substring(i);
                    answer = allProducable(candidateCekimEkleri, cekimEkleriStr);
                    if (answer != null) {
                        Word wordRoot = getWord(root);
                        String type = wordRoot.getType();
                        for (WordDetail filt : filtered) {
                            type = ((YapımEki) filt.getEkler().get(filt.getEkler().size() - 1)).getTo();
                            wds = markSuffixes(answer, root, false, true);
                            ArrayList<WordDetail> filtered2 = filter(wds, false, type);
                            if (filtered2.size() > 0) {
                                for (WordDetail morpCekim : filtered2) {
                                    WordDetail real = new WordDetail(root, null);
                                    List<Ek> allEksBelongingReal = (List<Ek>) filt.getEkler().clone();
                                    allEksBelongingReal.addAll(morpCekim.getEkler());
                                    real.setEkler((ArrayList<Ek>) allEksBelongingReal);
                                    results.add(real);
                                }

                            }
                        }
                    }
                }
            }

        }
        // for yapım ekleri
        ArrayList<String> answer = allProducable(ekler, yapimEkleriStr);
        ArrayList<WordDetail> filtered = new ArrayList<>();
        if (answer != null) {
            ArrayList<WordDetail> wds = markSuffixes(answer, root, true, false);
            filtered = filter(wds, true, "");
            if (filtered.size() > 0) {
                results.addAll(filtered);
            }
        }
        // for çekim ekleri
        answer = allProducable(ekler, cekimEkleriStr);
        filtered = new ArrayList<>();
        if (answer != null) {
            ArrayList<WordDetail> wds = markSuffixes(answer, root, false, true);
            filtered = filter(wds, false, "");
            if (filtered.size() > 0) {
                results.addAll(filtered);
            }
        }
        return results;
    }


    /**
     * reads and parses TDK Dictionary into the memory.
     * <p>
     * Each word in the TDK dictionary, apart from the ones that do not have a proper lexical class,
     * will be created as an instance of Word class and will be put in turkish ArrayList.
     */
    public static void readTurkish() throws IOException {
        BufferedReader read;
        String str;
        for (int i = 0; i < 28; i++) { //for each of the files
            String currentFileName =  "HARF_" + alphabet.get(i) + ".xml";
            InputStream bytes = Core.class.getClassLoader().getResourceAsStream(currentFileName);
            Reader chars = new InputStreamReader(bytes, "UTF8");
            read = new BufferedReader(chars);
            while ((str = read.readLine()) != null) {

                if (str.contains("<name>")) {
                    String afterNameTag = str.substring(str.indexOf("<name>") + 7, str.indexOf("</name>")).trim();

                    if (!afterNameTag.contains(" ") || afterNameTag.contains("(I")) {
                        boolean isok = true;
                        afterNameTag = afterNameTag.split(" ")[0];
                        while ((str = read.readLine()) != null && !str.contains("<lex_class>")) {

                        }
                        if (str == null) {
                            str = "";
                        }
                        String lexClass = str.substring(str.indexOf("<lex_class>") + 11, str.indexOf("</lex_class>")).trim();
                        String wordType = "";
                        if (lexClass.contains("isim")) {
                            wordType = "isim";
                        } else if (lexClass.contains("fiil")) {
                            wordType = "fiil";
                        } else if (lexClass.contains("sıfat")) {
                            wordType = "sıfat";
                        } else if (lexClass.contains("zarf")) {
                            wordType = "zarf";
                        } else {
                            isok = false;
                        }
                        if (isok) {
                            Word word = new Word(afterNameTag.toLowerCase(), wordType);
                            while ((str = read.readLine()) != null && !str.contains("<meaning_text>")) {

                            }
                            if (str == null) {
                                str = "";
                            }
                            String meaning_text = str.substring(str.indexOf("<meaning_text>") + 14, str.indexOf("</meaning_text>")).trim();
                            if (word.getContent().startsWith("yaba")) {
                                int a;
                            }
                            if (meaning_text.contains(" işi") && (word.getContent().endsWith("ma") || word.getContent().endsWith("me"))) {
                                turkish.add(word);
                                word = new Word(afterNameTag.substring(0, afterNameTag.length() - 2).toLowerCase(), "fiil");
                            }
                            turkish.add(word);
                        }

                    }

                }
            }
        }
    }

    private static ArrayList<String> testWords(String input, boolean onlycekim) {
        ArrayList<String> result = new ArrayList<>();
        boolean isAnyCekimEkiCombinationFound = false;
        if (turkish.contains(new Word(input, "")) && onlycekim) {
            result.add(input);
        }

        for (int i = 1; i < input.length(); i++) {
            if (turkish.contains(new Word(input.substring(0, i), ""))) {
                int lastindex = input.length();
                String tempinp = input.substring(i);
                if (tempinp.contains("ğ")) {
                    lastindex = tempinp.lastIndexOf("ğ");
                    tempinp = tempinp.substring(0, lastindex) + "k";
                }
                ArrayList<String> answer = allProducable(tempinp, cekimEkleriStr); // if null it is not produce-able
                ArrayList<WordDetail> filtered = new ArrayList<>();
                if (answer != null) {
                    ArrayList<WordDetail> wds = markSuffixes(answer, input.substring(0, i), false, true);
                    filtered = filter(wds, false, "");
                } // 1
                if (filtered.size() != 0) {
                    if (onlycekim) {
                        result.add(input.substring(0, i));

                    } else {
                        checkYapimEkleri(input.substring(0, i), result);
                        isAnyCekimEkiCombinationFound = true;
                    }
                }
            }
        }
        if (!isAnyCekimEkiCombinationFound && !onlycekim) {
            checkYapimEkleri(input, result);
        }
        if (result.size() < 1) {
            int lastindex = input.length();
            if (input.substring(input.length() / 2).contains("ğ")) {
                lastindex = input.lastIndexOf("ğ");
                input = input.substring(0, lastindex) + "k";
            }
            result.add(input);
        }
        return result;
        //  System.out.println("bitti");
    }

    public static void checkYapimEkleri(String rootWithYapimEkleri, ArrayList<String> res) {
        // cekim eki kombinasyonu found at this point
        // possible root can be with yapim eks
        boolean anyYapimEkiCombinationFound = false;
        for (int j = 1; j < rootWithYapimEkleri.length(); j++) {
            String rootCandidateWithoutYapimEkis = rootWithYapimEkleri.substring(0, j);
            if (turkishRoots.contains(new Word(rootCandidateWithoutYapimEkis, ""))) {
                ArrayList<String> answer2 = allProducable(rootWithYapimEkleri.substring(j), yapimEkleriStr);
                ArrayList<WordDetail> filtered2 = new ArrayList<>();
                if (answer2 != null) {
                    ArrayList<WordDetail> wds2 = markSuffixes(answer2, rootCandidateWithoutYapimEkis, true, false);
                    filtered2 = filter(wds2, true, "");
                }
                if (filtered2.size() != 0) {
                    res.add(rootCandidateWithoutYapimEkis);
                    anyYapimEkiCombinationFound = true;
                }
            }
        }
        if (!anyYapimEkiCombinationFound) {

            res.add(rootWithYapimEkleri);
        }

    }

    public static ArrayList<WordDetail> filter(ArrayList<WordDetail> wds, boolean isYapımEki, String type) {
        ArrayList<WordDetail> filtered = new ArrayList<>();
        for (WordDetail wd : wds) {
            boolean isOkay = true;
            ArrayList<Ek> ekler = wd.getEkler();
            String currentState = getWord(wd.getRoot()).getType();
            if (!type.equals("")) {
                currentState = type;
            }
            if (!isYapımEki && currentState.equals("sıfat")) {
                currentState = "isim";
            }
            for (Ek ek : ekler) {
                if (ek instanceof YapımEki) {
                    YapımEki yek = (YapımEki) ek;
                    if (!currentState.equals(yek.getFrom())) {
                        isOkay = false;
                        break;
                    } else {
                        currentState = yek.getTo();
                        if (!isYapımEki && currentState.equals("sıfat")) {
                            currentState = "isim";
                        }
                    }
                }
                if (ek instanceof CekimEki) {
                    CekimEki cek = (CekimEki) ek;
                    String from = cek.getFrom();
                    if (cek.getFrom().equals("sıfat")) {
                        from = "isim";
                    }
                    if (!currentState.equals(from)) {
                        isOkay = false;
                        break;
                    }
                }
            }
            if (isOkay) {
                filtered.add(wd);
            }
        }
        return filtered;
    }

    /**
     * Returns the instance of Word from the turkish list w.r.t. given String
     *
     * @param content content of the wanted Word
     * @return an instance of Word if found, null otherwise.
     */
    public static Word getWord(String content) {
        for (Word w : turkish) {
            if (w.getContent().equals(content))
                return w;

        }
        return null;
    }

    /**
     * Returns all the instances of Ek w.r.t. given String, considers only the derivational suffixes.
     *
     * @param content content of the wanted Ek
     * @return a list of Eks
     */
    public static ArrayList<Ek> getYapımEkleri(String content) {
        ArrayList<Ek> result = new ArrayList<>();
        for (YapımEki ek :
                yapımEkleri) {
            if (content.equals(ek.getEk())) {
                result.add(ek);
            }
        }
        if (result.size() == 0) return null;
        return result;
    }

    /**
     * Returns all the instances of Ek w.r.t. given String, considers only the inflectional suffixes.
     *
     * @param content content of the wanted Ek
     * @return a list of Eks
     */
    public static ArrayList<Ek> getCekimEkleri(String content) {
        ArrayList<Ek> result = new ArrayList<>();
        for (CekimEki ek :
                cekimEkleri) {
            if (content.equals(ek.getEk())) {
                result.add(ek);
            }
        }
        if (result.size() == 0) return null;
        return result;
    }

    public static ArrayList<WordDetail> markSuffixes(ArrayList<String> outputs, String root, boolean isyapim, boolean iscekim) {
        ArrayList<WordDetail> res = new ArrayList<>();
        ArrayList<WordDetail> resAll = new ArrayList<>();
        ArrayList<WordDetail> trueRes = new ArrayList<>();
        String correctOutput = "";
        for (String possibleOutcome : outputs) {
            correctOutput = possibleOutcome.replaceAll("-", "");
            String[] eklerListesi = possibleOutcome.split("-");
            ArrayList<WordDetail> temp = new ArrayList<>();
            for (int i = 0; i < eklerListesi.length; i++) {
                String possEk = eklerListesi[i];
                ArrayList<Ek> yapEks = getYapımEkleri(possEk);
                ArrayList<Ek> cekEks = getCekimEkleri(possEk);
                temp = new ArrayList<>();
                if (yapEks != null && isyapim) {
                    for (int j = 0; j < yapEks.size(); j++) {
                        if (i == 0) {
                            ArrayList<Ek> asd = new ArrayList<>();
                            asd.add(yapEks.get(j));
                            WordDetail wd = new WordDetail(root, asd);
                            temp.add(wd);
                        } else {
                            for (WordDetail wdt : res) {
                                ArrayList<Ek> asd = (ArrayList<Ek>) wdt.getEkler().clone();
                                asd.add(yapEks.get(j));
                                WordDetail wd = new WordDetail(root, asd);

                                String ress = "";
                                for (int k = 0; k < wd.getEkler().size(); k++) {
                                    ress += wd.getEkler().get(k).getEk();
                                }
                                if (ress.length() < correctOutput.length() || ress.equals(correctOutput)) {
                                    temp.add(wd);
                                }
                            }
                        }
                    }
                }
                if (cekEks != null && iscekim) {
                    for (Ek e : cekEks) {
                        if (i == 0) {
                            ArrayList<Ek> asd = new ArrayList<>();
                            asd.add(e);
                            WordDetail wd = new WordDetail(root, asd);
                            temp.add(wd);
                        } else {
                            for (WordDetail wdt : res) {
                                ArrayList<Ek> asd = (ArrayList<Ek>) wdt.getEkler().clone();
                                asd.add(e);
                                WordDetail wd = new WordDetail(root, asd);
                                String ress = "";
                                for (int k = 0; k < wd.getEkler().size(); k++) {
                                    ress += wd.getEkler().get(k).getEk();
                                }
                                if (ress.length() < correctOutput.length() || ress.equals(correctOutput)) {
                                    if (ress.equals(correctOutput)) {
                                        // if (wd.getEkler().size() * 2 <= correctOutput.length()) {
                                        temp.add(wd);
                                        //    }
                                    } else {
                                        temp.add(wd);
                                    }

                                }
                            }
                        }
                    }
                }
                res = temp;
            }
            resAll.addAll(res);

        }
        HashSet<WordDetail> tempos = new HashSet<>();
        for (WordDetail wd : resAll) {
            String ress = "";
            for (int i = 0; i < wd.getEkler().size(); i++) {
                ress += wd.getEkler().get(i).getEk();
            }
            if (ress.equals(correctOutput)) {
                tempos.add(wd);
            }
        }
        trueRes.addAll(tempos);
        return trueRes;
    }

    public static ArrayList<String> allProducable(String sequence, ArrayList<String> allEks) {
        ArrayList<String> allPoss = new ArrayList<>();
        String res = "";
        if (allEks.contains(sequence)) allPoss.add(sequence);
        for (int i = 1; i < sequence.length(); i++) {
            String left = sequence.substring(0, i);
            String right = sequence.substring(i);
            if (allEks.contains(left) && allEks.contains(right)) {
                allPoss.add(left + "-" + right);
            } else {
                ArrayList<String> leftPoss = allProducable(left, allEks);
                ArrayList<String> rightPoss = allProducable(right, allEks);
                if (leftPoss != null && rightPoss != null) {
                    for (String leftPos : leftPoss) {
                        for (String rightPos : rightPoss) {
                            allPoss.add(leftPos + "-" + rightPos);
                        }

                    }
                }
            }

        }
        TreeSet<String> temp = new TreeSet<>();
        for (String s : allPoss) {
            temp.add(s);
        }
        ArrayList<String> finalAllPos = new ArrayList<>();
        for (String s : temp) {
            finalAllPos.add(s);
        }
        if (finalAllPos.size() > 0) return finalAllPos;
        return null;
    }

    /**
     * Reads the inflectional and derivational suffixes, as well as TurkishRoots from the local drive.
     *
     * @param filepath1                          /path/to/yapımekleri.txt
     * @param filepath2                          /path/to/cekimekleri.txt
     * @param filepath3/path/to/TurkishRoots.txt
     * @throws IOException
     */
    private static void readSuffixes(String filepath1, String filepath2, String filepath3) throws IOException {
        InputStream is = Core.class.getClassLoader().getResourceAsStream(filepath1);
        InputStreamReader read2 = new InputStreamReader(is, "UTF8");
        BufferedReader read = new BufferedReader(read2);
        String str;
        while ((str = read.readLine()) != null) {
            String[] temp2 = str.split(" \\* ");
            String[] ekler = temp2[0].trim().split(" ");
            String[] actions = temp2[1].trim().split(" ");
            for (int i = 0; i < ekler.length; i++) {
                YapımEki ek = new YapımEki("", "", "");
                ek.setFrom(actions[0]);
                ek.setTo(actions[1]);
                ek.setEk(ekler[i]);
                yapımEkleri.add(ek);
                yapimEkleriStr.add(ek.getEk());
            }
        }
        read.close();
        is = Core.class.getClassLoader().getResourceAsStream(filepath2);
        read2 = new InputStreamReader(is, "UTF8");
        read = new BufferedReader(read2);
        while ((str = read.readLine()) != null) {
            String[] temp2 = str.split(" \\* ");
            String ek = temp2[0].trim();
            String[] types = temp2[1].trim().split(" ");
            for (String type :
                    types) {
                cekimEkleri.add(new CekimEki(ek, type));
                cekimEkleriStr.add(ek);
            }
        }
        read.close();
        is = Core.class.getClassLoader().getResourceAsStream(filepath3);
        read2 = new InputStreamReader(is, "UTF8");
        read = new BufferedReader(read2);
        while ((str = read.readLine()) != null) {
            String[] temp2 = str.split(" ");
            String kök = temp2[0].trim();
            String type = temp2[1].trim();
            Word myWord = new Word(kök, type);
            turkishRoots.add(myWord);
        }
        read.close();
    }
}
