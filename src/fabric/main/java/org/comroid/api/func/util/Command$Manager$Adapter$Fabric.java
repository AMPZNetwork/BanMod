package org.comroid.api.func.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import lombok.Value;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.kyori.adventure.text.Component;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.UuidArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.comroid.api.Polyfill;
import org.comroid.api.data.seri.type.StandardValueType;
import org.comroid.api.data.seri.type.ValueType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.stream.Stream.*;
import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;
import static net.minecraft.server.command.CommandManager.*;

@Value
@Slf4j
@NonFinal
public class Command$Manager$Adapter$Fabric extends Command.Manager.Adapter implements CommandRegistrationCallback,
        com.mojang.brigadier.Command<ServerCommandSource>, SuggestionProvider<ServerCommandSource> {
    Command.Manager cmdr;

    public Command$Manager$Adapter$Fabric(Command.Manager cmdr) {
        this.cmdr = cmdr;

        cmdr.adapters.add(this);
    }

    private static Command.Node.@NotNull Call getCall(Command.Usage usage) throws CommandSyntaxException {
        Command.Node.Call call;
        if (usage.getNode() instanceof Command.Node.Group group)
            call = group.getDefaultCall();
        else if (usage.getNode() instanceof Command.Node.Call call0)
            call = call0;
        else {
            var message = Text.of("Command parsing error");
            throw new CommandSyntaxException(new SimpleCommandExceptionType(message), message);
        }
        if (call == null)
            throw new Command.Error("Not a command");
        return call;
    }

    public static Text component2text(Component component) {
        return Text.Serializer.fromJson(gson().serialize(component));
    }

    @Override
    public void initialize() {
        CommandRegistrationCallback.EVENT.register(this);
    }

    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher,
                         CommandRegistryAccess registryAccess,
                         RegistrationEnvironment environment) {
        final var helper = new Object() {

            LiteralArgumentBuilder<ServerCommandSource> convertNode(Command.Node node) {
                var base = literal(node.getName());
                if (node instanceof Command.Node.Call call) {
                    for (var parameter : call.getParameters()) {
                        var type = StandardValueType.forClass(parameter.getParam().getType());
                        base.then(argument(parameter.getName(), ArgumentConverter.VALUES.stream()
                                .filter(conv -> conv.valueType.equals(type))
                                .findAny()
                                .map(ArgumentConverter::getSupplier)
                                .map(Supplier::get)
                                .orElseGet(() -> Polyfill.uncheckedCast(StringArgumentType.string())))
                                .suggests(Command$Manager$Adapter$Fabric.this));
                    }
                    base.executes(Command$Manager$Adapter$Fabric.this);
                }
                if (node instanceof Command.Node.Group group) {
                    if (group.getDefaultCall() != null)
                        base.executes(Command$Manager$Adapter$Fabric.this);
                    group.nodes()
                            .map(this::convertNode)
                            .forEach(base::then);
                }
                return base;
            }
        };
        cmdr.getBaseNodes().stream()
                .map(helper::convertNode)
                .forEach(dispatcher::register);
    }

    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var fullCommand = context.getInput().split(" ");
        var adp = Command$Manager$Adapter$Fabric.this;
        var usage = cmdr.createUsageBase(adp, fullCommand, adp, context.getSource());
        try {
            usage.advanceFull();
            var call = getCall(usage);
            var args = new ConcurrentHashMap<String, Object>();
            call.getParameters().forEach(param -> {
                var key = param.getName();
                try {
                    var value = (Object) context.getArgument(key, param.getParam().getType());
                    args.put(key, value);
                } catch (IllegalArgumentException iaex) {
                    log.warn("Could not obtain argument " + key);
                }
            });
            cmdr.execute(this, fullCommand, args, concat(of(this, context.getSource()), streamExtraArgs())
                    .distinct().toArray());
            return 1;
        } catch (CommandSyntaxException csex) {
            throw csex;
        } catch (Throwable t) {
            var result = handleThrowable(t);
            handleResponse(usage, result);
            return 0;
        }
    }

    @Override
    public Stream<Command.Capability> capabilities() {
        return concat(super.capabilities(), of(Command.Capability.NAMED_ARGS));
    }

    protected Stream<Object> streamExtraArgs() {
        return empty();
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        return CompletableFuture.supplyAsync(() -> {
            var fullCommand = context.getInput().split(" ");
            var adp = Command$Manager$Adapter$Fabric.this;
            var usage = cmdr.createUsageBase(adp, fullCommand, adp);
            usage.advanceFull();
            return usage.getNode().nodes()
                    .map(here -> new Suggestion(context.getRange(), here.getName()))
                    .toList();
        }).thenApply(ls -> new Suggestions(context.getRange(), ls));
    }

    @Override
    public void handleResponse(Command.Usage command, @NotNull Object response, Object... args) {
        if (response instanceof CompletableFuture<?> future) {
            future.thenAccept(it -> handleResponse(command, it, args));
            return;
        }
        var source = Arrays.stream(args)
                .flatMap(Streams.cast(ServerCommandSource.class))
                .findAny().orElseThrow();
        if (response instanceof Component component)
            source.sendMessage(component2text(component));
        else source.sendMessage(Text.of(String.valueOf(response)));
    }

    @Value
    public static class ArgumentConverter<T> {
        public static final Set<ArgumentConverter<?>> VALUES = new HashSet<>();
        public static final ArgumentConverter<Boolean> BOOLEAN = new ArgumentConverter<>(StandardValueType.BOOLEAN, BoolArgumentType::bool);
        public static final ArgumentConverter<Double> DOUBLE = new ArgumentConverter<>(StandardValueType.DOUBLE, DoubleArgumentType::doubleArg);
        public static final ArgumentConverter<Float> FLOAT = new ArgumentConverter<>(StandardValueType.FLOAT, FloatArgumentType::floatArg);
        public static final ArgumentConverter<Integer> INTEGER = new ArgumentConverter<>(StandardValueType.INTEGER, IntegerArgumentType::integer);
        public static final ArgumentConverter<Long> LONG = new ArgumentConverter<>(StandardValueType.LONG, LongArgumentType::longArg);
        public static final ArgumentConverter<String> STRING = new ArgumentConverter<>(StandardValueType.STRING, StringArgumentType::string);
        public static final ArgumentConverter<java.util.UUID> UUID = new ArgumentConverter<>(StandardValueType.UUID, UuidArgumentType::uuid);
        ValueType<T> valueType;
        Supplier<ArgumentType<T>> supplier;

        {
            VALUES.add(this);
        }
    }
}
