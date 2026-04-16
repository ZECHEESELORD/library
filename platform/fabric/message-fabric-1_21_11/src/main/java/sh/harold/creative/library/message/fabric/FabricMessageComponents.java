package sh.harold.creative.library.message.fabric;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import sh.harold.creative.library.message.InlineMessage;
import sh.harold.creative.library.message.MessageBlock;

import java.util.Objects;

public final class FabricMessageComponents {

    private FabricMessageComponents() {
    }

    public static Component renderChat(InlineMessage message) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(message, "message").send(capture);
        return capture.requireChat();
    }

    public static Component renderActionBar(InlineMessage message) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(message, "message").sendActionBar(capture);
        return capture.requireActionBar();
    }

    public static Component renderChat(MessageBlock block) {
        CaptureAudience capture = new CaptureAudience();
        Objects.requireNonNull(block, "block").send(capture);
        return capture.requireChat();
    }

    private static final class CaptureAudience implements Audience {

        private Component chat;
        private Component actionBar;

        @Override
        public void sendMessage(Component message) {
            this.chat = Objects.requireNonNull(message, "message");
        }

        @Override
        public void sendActionBar(Component message) {
            this.actionBar = Objects.requireNonNull(message, "message");
        }

        private Component requireChat() {
            if (chat == null) {
                throw new IllegalStateException("Message did not render chat output");
            }
            return chat;
        }

        private Component requireActionBar() {
            if (actionBar == null) {
                throw new IllegalStateException("Message did not render action-bar output");
            }
            return actionBar;
        }
    }
}
