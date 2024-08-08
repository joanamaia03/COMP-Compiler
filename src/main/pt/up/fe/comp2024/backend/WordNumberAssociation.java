package pt.up.fe.comp2024.backend;

import java.util.HashMap;
import java.util.Map;

public class WordNumberAssociation {
    private final Map<String, Integer> wordToNumberMap;

    public WordNumberAssociation() {
        wordToNumberMap = new HashMap<>();

        // Add hardcoded words and numbers
        wordToNumberMap.put("iload", 1);
        wordToNumberMap.put("aload", 1);
        wordToNumberMap.put("ldc", 1);
        wordToNumberMap.put("new", 1);
        wordToNumberMap.put("istore", -1);
        wordToNumberMap.put("astore", -1);
        wordToNumberMap.put("ifne", -1);
        wordToNumberMap.put("iaload", -1);
        wordToNumberMap.put("ireturn", -1);
        wordToNumberMap.put("areturn", -1);
        wordToNumberMap.put("iastore",-3);
        wordToNumberMap.put("putfield", -2);
        wordToNumberMap.put("iand", -1);
        wordToNumberMap.put("if_icmplt", -2);
        wordToNumberMap.put("if_icmple", -2);
        wordToNumberMap.put("if_icmpgt", -2);
        wordToNumberMap.put("if_icmpge", -2);
        wordToNumberMap.put("if_icmpeq", -2);
        wordToNumberMap.put("iadd", -1);
        wordToNumberMap.put("isub", -1);
        wordToNumberMap.put("imul", -1);
        wordToNumberMap.put("idiv", -1);
        wordToNumberMap.put("ixor", -1);
        wordToNumberMap.put("iconst",1);
        wordToNumberMap.put("bipush",1);
        wordToNumberMap.put("sipush",1);





        // Add more words and numbers as needed
    }

    public Integer getNumber(String word) {
        if (word.contains("_")) {
            word = word.substring(0, word.indexOf("_"));
        }
        return wordToNumberMap.get(word);
    }
    public Integer getNumberFromLine(String line){
        String[] words = line.split(" ");
        for (String word : words){
            if (!word.isBlank()){
                words[0] = word;
                break;
            }
        }
        if (words.length == 0 || getNumber(words[0]) == null ){
            return 0;
        }
        else{
            return getNumber(words[0]);
        }

    }


}
