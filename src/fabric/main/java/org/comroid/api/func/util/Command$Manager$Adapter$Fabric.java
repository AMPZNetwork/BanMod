package org.comroid.api.func.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.StringRange;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.comroid.api.Polyfill;
import org.comroid.api.data.seri.type.EnumValueType;
import org.comroid.api.data.seri.type.StandardValueType;
import org.comroid.api.data.seri.type.ValueType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static java.util.stream.Stream.*;
import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;
import static net.minecraft.server.command.CommandManager.*;
import static org.comroid.api.func.util.Debug.isDebug;
import static org.comroid.api.func.util.Streams.expand;
import static org.comroid.api.func.util.Streams.expandRecursive;

@Value
@Slf4j
@NonFinal
public class Command$Manager$Adapter$Fabric extends Command.Manager.Adapter
        implements Command.Handler.Minecraft, CommandRegistrationCallback,
        com.mojang.brigadier.Command<ServerCommandSource>, SuggestionProvider<ServerCommandSource> {
    Set<Command.Capability> capabilities = Set.of(Command.Capability.NAMED_ARGS);
    Command.Manager cmdr;

    public Command$Manager$Adapter$Fabric(Command.Manager cmdr) {
        this.cmdr = cmdr;

        cmdr.addChildren(this);
    }

    @Override
    public void register(CommandDispatcher<ServerCommandSource> dispatcher,
                         CommandRegistryAccess registryAccess,
                         RegistrationEnvironment environment) {
        cmdr.getBaseNodes().stream()
                // recurse into subcommand nodes
                .flatMap(node -> convertNode("[Fabric Command Adapter Debug] -", node, 0))
                .distinct()
                .forEach(dispatcher::register);
    }

    public static Text component2text(Component component) {
        return Text.Serializer.fromJson(gson().serialize(component));
    }

    @Override
    public Stream<Object> expandContext(Object... context) {
        return super.expandContext(context).collect(expandRecursive(it -> {
            if (it instanceof CommandContext<?> ctx)
                return of(ctx.getSource());
            if (it instanceof ServerCommandSource scs)
                return of(scs.getPlayer());
            if (it instanceof ServerPlayerEntity player)
                return of(player.getUuid());
            return empty();
        }));
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

    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        var fullCommand = context.getInput().split(" ");
        Command.Usage usage;
        try {
            usage = cmdr.createUsageBase(this, fullCommand, context);
        } catch (Throwable t) {
            log.warn("An internal error occurred during command preparation", t);
            var result = handleThrowable(t);
            handleResponse(Command.Usage.builder()
                    .manager(cmdr)
                    .fullCommand(fullCommand)
                    .source(this)
                    .build(), result, context);
            return 0;
        }
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
                    log.warn("Could not obtain argument {}", key);
                    var defaultValue = param.defaultValue();
                    if (defaultValue != null)
                        args.put(key, defaultValue);
                }
            });
            cmdr.execute(usage, args);
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
    public void initialize() {
        CommandRegistrationCallback.EVENT.register(this);
    }

    @Override
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context,
                                                         SuggestionsBuilder builder) {
        var input = context.getInput().substring(1); // strip leading slash
        var inLen = input.length() + 1;
        var split = input.split(" ");
        var lsWrd = split.length <= input.chars().filter(c -> c == ' ').count() ? "" : split[split.length - 1];
        var lwLen = lsWrd.length();
        var range = new StringRange(inLen - lwLen, inLen);
        return CompletableFuture.supplyAsync(() -> {
                    var fullCommand = input.split(" ");
                    var usage = cmdr.createUsageBase(this, fullCommand, context);
                    usage.advanceFull();
                    return usage.getNode().nodes()
                            .skip(split.length - (lsWrd.isEmpty() ? 1 : 2) - usage.getCallIndex())
                            .limit(1)
                            .flatMap(n0 -> (n0 instanceof Command.Node.Callable callable
                                    ? callable.nodes()
                                    .map(node -> node instanceof Command.Node.Parameter parameter
                                            ? "<%s>".formatted(parameter.getName())
                                            : node.getName())
                                    : n0 instanceof Command.AutoFillProvider provider
                                    ? provider.autoFill(usage, n0.getName(), lsWrd)
                                    : Stream.<String>empty())
                                    .map(String::trim)
                                    .filter(str -> str.toLowerCase().startsWith(lsWrd.toLowerCase()))
                                    .map(str -> new Suggestion(range, str,
                                            Text.of(n0.getName() + ": " + n0.getDescription()))))
                            .toList();
                })
                .thenApply(ls -> new Suggestions(range, ls))
                .exceptionally(Polyfill.exceptionLogger());
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

    private Stream<LiteralArgumentBuilder<ServerCommandSource>> convertNode(String pad, Command.Node node, int rec) {
        // this node
        final var base = literal(node.getName());
        if (isDebug()) System.out.printf("%s Command '%s'\n", pad, base.getLiteral());

        if (node instanceof Command.Node.Call call) {
            var parameters = call.getParameters();
            if (!parameters.isEmpty()) {
                // convert parameter nodes recursively
                var param = convertParam(pad + "->", 0, parameters.toArray(new Command.Node.Parameter[0]));

                // set base executable if there is no (required) parameters
                if (parameters.stream().allMatch(not(Command.Node.Parameter::isRequired))) {
                    base.executes(this);
                    if (isDebug()) System.out.printf("%s-> Can be executed because no parameters are required\n", pad);
                }

                // append parameter
                base.then(param);
            } else {
                base.executes(this);
                if (isDebug()) System.out.printf("%s-> Can be executed because it has no parameters\n", pad);
            }
        } else if (node instanceof Command.Node.Group group) {
            // add execution layer if group is callable
            if (group.getDefaultCall() != null)
                base.executes(this);

            var subNodes = group.nodes()
                    // recurse into subcommand nodes
                    .flatMap(sub -> convertNode(pad + " -", sub, rec + 1))
                    .peek(base::then)
                    .toList();
            return concat(of(base), subNodes.stream())
                    .flatMap(expand(it -> createAliasRedirects(pad, node, it)));
        }

        return of(base).flatMap(expand(it -> createAliasRedirects(pad, node, it)));
    }

    private RequiredArgumentBuilder<ServerCommandSource, ?> convertParam(
            String pad,
            int level,
            Command.Node.Parameter... parameters
    ) {
        if (level >= parameters.length)
            throw new IllegalStateException("Recursion limit exceeded");

        final var parameter = parameters[level];

        // find argument type minecraft representation
        var argType = (switch (parameter.getAttribute().stringMode()) {
            case NORMAL -> ArgumentConverter.blob(parameter);
            case GREEDY -> ArgumentConverter.GREEDY_STRING;
            case SINGLE_WORD -> ArgumentConverter.WORD;
        }).supplier.get();
        var arg = argument(parameter.name(), argType).suggests(this);
        if (isDebug()) System.out.printf("%s Argument '%s: %s'\n", pad, argType, parameter.name());

        // try recurse deeper
        if (level + 1 < parameters.length) {
            // set executable if followup parameter(s) are not required
            if (!parameters[level + 1].isRequired()) {
                arg.executes(this);
                if (isDebug())
                    System.out.printf("%s-> Can be executed because followup parameters are not required\n", pad);
            }

            // convert & append next parameter
            var next = convertParam(pad + "->", level + 1, parameters);
            arg.then(next);
        } else {
            // last one is always executable
            arg.executes(this);
            if (isDebug()) System.out.printf("%s-> Can be executed because it is last in the chain\n", pad);
        }

        return arg;
    }

    private Stream<LiteralArgumentBuilder<ServerCommandSource>> createAliasRedirects(
            String pad,
            Command.Node desc,
            LiteralArgumentBuilder<ServerCommandSource> target) {
        return desc.aliases()
                .filter(not(desc.getName()::equals))
                .map(alias -> {
                    var redirect = literal(alias).redirect(target.build());
                    if (isDebug()) System.out.printf("%s-> Alias: '%s'\n", pad, alias);
                    return redirect;
                });
    }

    @Value
    public static class ArgumentConverter {
        public static final Map<Class<?>, ArgumentConverter> CACHE = new ConcurrentHashMap<>();
        public static final ArgumentConverter BOOLEAN = new ArgumentConverter(StandardValueType.BOOLEAN, BoolArgumentType::bool);
        public static final ArgumentConverter DOUBLE = new ArgumentConverter(StandardValueType.DOUBLE, DoubleArgumentType::doubleArg);
        public static final ArgumentConverter FLOAT = new ArgumentConverter(StandardValueType.FLOAT, FloatArgumentType::floatArg);
        public static final ArgumentConverter INTEGER = new ArgumentConverter(StandardValueType.INTEGER, IntegerArgumentType::integer);
        public static final ArgumentConverter LONG = new ArgumentConverter(StandardValueType.LONG, LongArgumentType::longArg);
        public static final ArgumentConverter WORD = new ArgumentConverter(StandardValueType.STRING, StringArgumentType::word);
        public static final ArgumentConverter STRING = new ArgumentConverter(StandardValueType.STRING, StringArgumentType::string);
        public static final ArgumentConverter GREEDY_STRING = new ArgumentConverter(StandardValueType.STRING, StringArgumentType::greedyString);
        public static final ArgumentConverter UUID = new ArgumentConverter(StandardValueType.UUID, UuidArgumentType::uuid);
        ValueType<?> valueType;
        Supplier<ArgumentType<?>> supplier;

        public ArgumentConverter(ValueType<?> valueType, Supplier<ArgumentType<?>> supplier) {
            this.valueType = valueType;
            this.supplier = supplier;

            CACHE.put(valueType.getTargetClass(), this);
        }

        public static <T> ArgumentConverter blob(Command.Node.Parameter parameter) {
            var type = parameter.getParam().getType();
            return type.isEnum()
                    ? CACHE.computeIfAbsent(type, k -> new ArgumentConverter(Polyfill
                    .<ValueType<T>>uncheckedCast(EnumValueType.of(type)), StringArgumentType::word))
                    : StandardValueType.forClass(type)
                    .stream()
                    .flatMap(t0 -> ArgumentConverter.CACHE.values().stream()
                            .filter(conv -> conv.valueType.equals(t0)))
                    .findAny()
                    .orElseGet(() -> Polyfill.uncheckedCast(StringArgumentType.string()));
        }
    }
}
