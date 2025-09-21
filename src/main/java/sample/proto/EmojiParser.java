package sample.proto;

import java.util.HashMap;
import java.util.Map;



public class EmojiParser {

    private static final Map<String, Integer> EMOJI_MAP = Map.of(
            "smile", 0x1F604,
            "laugh", 0x1F602,
            "thumbsup", 0x1F44D,
            "heart", 0x2764
    );


    public static String parseEmoji(String input)  {
           input = input.trim();

            if (input.codePoints().count() == 1 && Character.isSurrogate(input.charAt(0))) {
                return input;
            }

            if (input.startsWith("0x")) {
                int codePoint = Integer.decode(input);
                return new String(Character.toChars(codePoint));
            }

            if (input.startsWith(":") && input.endsWith(":")) {
                String key = input.substring(1, input.length() - 1);
                Integer codePoint = EMOJI_MAP.get(key);
                if (codePoint != null) {
                    return new String(Character.toChars(codePoint));
                }
            }
            return null;
    }
}
