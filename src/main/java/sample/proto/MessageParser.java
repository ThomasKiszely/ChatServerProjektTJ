package sample.proto;

import sample.domain.Message;



public interface MessageParser {
    Message parseMessage(String message) throws ParseException;
}
