package org.comroid.api.func.util;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
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

import static java.util.function.Predicate.not;
import static java.util.stream.Stream.*;
import static net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson;
import static net.minecraft.server.command.CommandManager.*;
import static org.comroid.api.func.util.Debug.isDebug;
import static org.comroid.api.func.util.Streams.expand;

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
        cmdr.getBaseNodes().stream()
                // recurse into subcommand nodes
                .flatMap(node -> convertNode("[Fabric Command Adapter Debug] -", node, 0))
                .distinct()
                .forEach(dispatcher::register);
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
        var argType = ArgumentConverter.blob(parameter);
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
    public CompletableFuture<Suggestions> getSuggestions(CommandContext<ServerCommandSource> context,
                                                         SuggestionsBuilder builder) throws CommandSyntaxException {
        return CompletableFuture.supplyAsync(() -> {
            var fullCommand = context.getInput().split(" ");
            var adp = Command$Manager$Adapter$Fabric.this;
            var usage = cmdr.createUsageBase(adp, fullCommand, adp);
            usage.advanceFull();
            return usage.getNode().nodes()
                    .map(here -> new Suggestion(context.getRange(), here.getName()))
                    //.collect(Streams.append(new Suggestion(context.getRange(), "tst")))
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

    @Override
    public String handleThrowable(Throwable throwable) {
        return "§c" + super.handleThrowable(throwable);
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

        public static ArgumentType<?> blob(Command.Node.Parameter parameter) {
            return StandardValueType.forClass(parameter.getParam().getType())
                    .stream()
                    .flatMap(type -> ArgumentConverter.VALUES.stream()
                            .filter(conv -> conv.valueType.equals(type)))
                    .findAny()
                    .map(conv -> conv.supplier.get())
                    .orElseGet(() -> Polyfill.uncheckedCast(StringArgumentType.string()));
        }
    }
}
