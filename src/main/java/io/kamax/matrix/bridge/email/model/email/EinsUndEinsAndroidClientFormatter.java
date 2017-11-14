/*
 * matrix-appservice-email - Matrix Bridge to E-mail
 * Copyright (C) 2017 Maxime Dor
 *
 * https://max.kamax.io/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.kamax.matrix.bridge.email.model.email;

import io.kamax.matrix.bridge.email.model._BridgeMessageContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Whitelist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.mail.Message;
import javax.mail.MessagingException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Message content contained in div of class mail_android_message ending with a signature ad.
 * Quote in separate div of class mail_android_quote. Actual quoted content in div of class gmail_quote.
 * Additionally the whole html/body is wrapped in another html/body block.
 *
 * Used at least by the Android apps of GMX.net and WEB.de.
 * Both apps were produced by 1&1 (1und1.de), hence the name of this class (German reading of 1&1).
 */
@Component
public class EinsUndEinsAndroidClientFormatter extends AEmailClientFormatter {

    private Logger log = LoggerFactory.getLogger(EinsUndEinsAndroidClientFormatter.class);

    private Pattern pattern = Pattern.compile("<div class=\"mail_android_message\">");
    private Pattern signature = Pattern.compile("Diese Nachricht wurde von meinem Android Mobiltelefon mit .+ Mail gesendet.");

    @Override
    public String getId() {
        return "1und1-android";
    }

    @Override
    public boolean matches(Message m, List<_BridgeMessageContent> contents) throws MessagingException {
        for (_BridgeMessageContent content : contents) {
            Matcher matcher = pattern.matcher(content.getContentAsString());
            if (matcher.find()) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected String formatHtml(String content) {
        Element body = Jsoup.parse(content).body();
        Element contentDiv = body.select(".mail_android_message").first();
        if (contentDiv == null) {
            log.warn("Found no valid content in e-mail from 1und1, returning empty");
            return "";
        }

        if (signature.matcher(content).find()) {
            Element copy = contentDiv.clone();
            String removedText = "";
            while (copy.childNodes().size() > 0) {
                Node child = copy.childNodes().get(copy.childNodeSize() - 1);
                child.remove();
                if (child instanceof Element) {
                    removedText = ((Element) child).text() + removedText;
                } else if (child instanceof TextNode) {
                    removedText = ((TextNode) child).text() + removedText;
                }
                if (signature.matcher(removedText.trim()).matches()) {
                    // Successfully removed signature
                    contentDiv = copy;
                    // Try to remove the remaining separator line
                    child = contentDiv.childNode(contentDiv.childNodeSize() - 1);
                    if (child instanceof Element && ((Element) child).is("br")) {
                        child.remove();
                        child = contentDiv.childNode(contentDiv.childNodeSize() - 1);
                        if (child instanceof TextNode && ((TextNode) child).text().trim().equals("--")) {
                            child.remove();
                        }
                    }
                    break;
                }
            }
        }

        removeDanglingNewlines(contentDiv);

        return Jsoup.clean(contentDiv.html(), Whitelist.basic());
    }

}
