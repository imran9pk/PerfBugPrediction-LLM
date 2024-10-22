package io.crate.exceptions;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserUnknownException extends ResourceUnknownException implements UnscopedException {

    public UserUnknownException(String userName) {
        super(getMessage(Collections.singletonList(userName)));
    }

    public UserUnknownException(List<String> userNames) {
        super(getMessage(userNames));
    }

    private static String getMessage(List<String> userNames) {
        assert userNames.isEmpty() == false : "At least one username must be provided";
        if (userNames.size() == 1) {
            return String.format(Locale.ENGLISH, "User '%s' does not exist", userNames.get(0));
        }
        return String.format(Locale.ENGLISH, "Users '%s' do not exist", String.join(", ", userNames));
    }
}
