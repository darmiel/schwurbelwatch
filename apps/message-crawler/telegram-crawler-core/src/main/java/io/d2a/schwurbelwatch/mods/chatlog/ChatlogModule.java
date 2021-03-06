package io.d2a.schwurbelwatch.mods.chatlog;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import io.d2a.schwurbelwatch.mods.Accounts;
import io.d2a.schwurbelwatch.tgcrawler.api.SwApi;
import io.d2a.schwurbelwatch.tgcrawler.api.response.DatabaseResult;
import io.d2a.schwurbelwatch.tgcrawler.api.routes.base.ContentType;
import io.d2a.schwurbelwatch.tgcrawler.api.routes.messages.ApiMessage;
import io.d2a.schwurbelwatch.tgcrawler.api.routes.messages.MessageService;
import io.d2a.schwurbelwatch.tgcrawler.core.client.TelegramClient;
import io.d2a.schwurbelwatch.tgcrawler.core.logging.AnsiColor;
import io.d2a.schwurbelwatch.tgcrawler.core.logging.Logger;
import io.d2a.schwurbelwatch.tgcrawler.core.message.SimpleChatMessage;
import io.d2a.schwurbelwatch.tgcrawler.core.module.BotModule;
import io.d2a.schwurbelwatch.tgcrawler.core.module.Module;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.drinkless.tdlib.TdApi;
import org.drinkless.tdlib.TdApi.GetMessage;
import org.drinkless.tdlib.TdApi.UpdateDeleteMessages;
import org.drinkless.tdlib.TdApi.UpdateMessageEdited;
import org.drinkless.tdlib.TdApi.UpdateNewMessage;
import retrofit2.Call;
import retrofit2.Response;

@Module(
    name = "Messages 2 Database",
    description = "Stores all message to the database via rest-api",
    version = "1.0",
    author = "darmiel <hi@d2a.io>"
)
public class ChatlogModule extends BotModule {

  private TelegramClient client;

  private final MessageService service = SwApi.MESSAGE_SERVICE;

  private final Map<Integer, ContentType> contentTypeMap = new HashMap<>();

  @Override
  public void onEnable() {
    // Main account for listening for messages: walterheldcorona
    final Optional<TelegramClient> clientOptional = findTelegramClient(Accounts.WATCHER_1);
    if (clientOptional.isPresent()) {
      this.client = clientOptional.get();
      this.client.registerListeners(this);
      Logger.success("Registered listener for client: " + this.client);
    } else {
      Logger.warn("Client '" + Accounts.WATCHER_1 + "' not found");
    }

    updateContentTypes();
  }

  private void updateContentTypes() {
    // Update content types
    final Response<List<ContentType>> execute;

    try {
      execute = SwApi.BASE_SERVICE.getContentTypes().execute();
    } catch (IOException e) {
      e.printStackTrace();
      Logger.error(e);
      return;
    }

    final List<ContentType> body = execute.body();
    if (body == null) {
      Logger.error("Failed to update content type. Response:");
      Logger.error(execute);
      return;
    }

    contentTypeMap.clear();
    for (final ContentType contentType : body) {
      contentTypeMap.put(contentType.typeId, contentType);
    }

    Logger.info("Updated Content Types: " + contentTypeMap.size() + " types found.");
  }

  private void updateInsertMessage(@Nonnull final TdApi.Message tdMessage, boolean edit) {
    final ApiMessage msg = ApiMessage.wrap(tdMessage, contentTypeMap);
    final SimpleChatMessage dcm = SimpleChatMessage.wrap(tdMessage);
    if (msg == null || dcm == null) {
      return;
    }

    // Short preview text
    String shortText = "[empty]";
    if (msg.content != null) {
      shortText = msg.content;
      if (shortText.length() > 54) {
        shortText = msg.content.substring(0, 53) + "...";
      }
      shortText = shortText.replace("\n", "[n]");
    }

    final String prefix = !edit ? AnsiColor.ANSI_PURPLE + "++ " : AnsiColor.ANSI_RED + "~~ ";

    Logger.info(AnsiColor.ANSI_CYAN + prefix + AnsiColor.ANSI_CYAN + msg.userId + ": " +
        shortText + AnsiColor.ANSI_RESET +
        " | T = " + dcm.getType() + " | HF = " + dcm.hasFiles() + " | FV = " + dcm.isFileValid());

    Logger.debug(dcm.getExtraG());

    // Skip messages with option save = False
    if (dcm.getType() != null && !dcm.getType().save) {
      Logger.info("Skipping message");
      return;
    }
    SwApi.callDatabaseResult(service.addMessage(msg));
  }

  @Subscribe
  public void onMessage(final UpdateNewMessage event) {
    final TdApi.Message message = event.message;
    updateInsertMessage(message, false);
  }

  @Subscribe
  public void onEdit(final UpdateMessageEdited event) {
    client.getClient().send(new GetMessage(event.chatId, event.messageId), object -> {
      if (object.getConstructor() == TdApi.Message.CONSTRUCTOR) {
        updateInsertMessage((TdApi.Message) object, true);
      }
    });
  }

  @Subscribe
  public void onDelete(final UpdateDeleteMessages event) {
    if (event.fromCache || !event.isPermanent) {
      return;
    }

    final JsonObject obj = new JsonObject();
    obj.addProperty("deleted_on", (System.currentTimeMillis()));

    for (final long messageId : event.messageIds) {
      Logger.info("-- " + messageId);
      final Call<DatabaseResult> call = service.updateMessage(messageId, obj);
      SwApi.callDatabaseResult(call);
    }
  }

}
