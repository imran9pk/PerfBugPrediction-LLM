package org.dcache.ftp.door;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Objects.requireNonNull;

import diskCacheV111.doors.TlsStarter;
import diskCacheV111.util.FsPath;
import dmg.util.CommandExitException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javax.net.ssl.SSLEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TlsFtpDoor extends WeakFtpDoorV1 implements TlsStarter {

    private static final Logger LOGGER = LoggerFactory.getLogger(TlsFtpDoor.class);
    private Consumer<SSLEngine> startTls;
    private final SSLEngine ssl;
    private final Set<String> _plaintextCommands = new HashSet<>();
    private boolean isChannelSecure;

    @Retention(RUNTIME)
    @Target(METHOD)
    @interface Plaintext {

    }

    public TlsFtpDoor(SSLEngine ssl, boolean allowUsernamePassword,
          Optional<String> anonymousUser, FsPath anonymousRoot,
          boolean requireAnonPasswordEmail) {
        super("FTPS", allowUsernamePassword, anonymousUser, anonymousRoot,
              requireAnonPasswordEmail);
        this.ssl = ssl;

        visitFtpCommands((m, cmd) -> {
            if (m.getAnnotation(Plaintext.class) != null) {
                _plaintextCommands.add(cmd);
            }
        });
    }

    @Override
    public void setTlsStarter(Consumer<SSLEngine> startTls) {
        this.startTls = requireNonNull(startTls);
    }

    @Override
    public void execute(String command) throws CommandExitException {
        ftpcommand(command, null, isChannelSecure ? ReplyType.TLS : ReplyType.CLEAR);
    }


    @Override
    protected void checkCommandAllowed(CommandRequest command, Object commandContext)
          throws FTPCommandException {
        boolean isPlaintextAllowed = _plaintextCommands.contains(command.getName());

        checkFTPCommand(isChannelSecure || isPlaintextAllowed,
              530, "Command not allowed until TLS is established");

        super.checkCommandAllowed(command, commandContext);
    }

    @Plaintext
    @Override
    public void ftp_feat(String arg) {
        super.ftp_feat(arg);
    }

    @Override
    protected StringBuilder buildFeatList(StringBuilder builder) {
        return super.buildFeatList(builder)
              .append(' ').append("AUTH SSL TLS").append("\r\n");
    }


    @Help("AUTH <SP> <arg> - Initiate secure context negotiation.")
    @Plaintext
    public void ftp_auth(String arg) throws FTPCommandException {
        LOGGER.info("going to authorize");

        checkFTPCommand(arg.equals("TLS") || arg.equals("SSL"),
              504, "Authenticating method not supported");
        checkFTPCommand(!isChannelSecure, 534, "TLS context already established");

        startTls.accept(ssl);
        isChannelSecure = true;
        reply("234 Ready for " + arg + " handshake");
    }
}
