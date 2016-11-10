import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.base64.Base64;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Created by Chen Wang on 2016/11/10 0010.
 */
public class VpnTester {
    private static String add = "";

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("VpnTest [Csv File]");
            return;
        }

        InputStreamReader reader;
        try {
            File file = new File("vpn.tp");
            reader = new InputStreamReader(new FileInputStream(file), "utf-8");
            BufferedReader br = new BufferedReader(reader);

            String line;
            for (; ; ) {
                line = br.readLine();
                if (line == null) break;
                add += line + "\r\n";
            }
        } catch (FileNotFoundException e) {
            System.out.println("No vpn.tp file.");
        } catch (IOException e) {
            System.out.println("Read vpn.tp failed:" + e.getMessage());
        }

        String filename = args[0];
        File file = new File(filename);

        try {
            reader = new InputStreamReader(new FileInputStream(file), "utf-8");
            BufferedReader br = new BufferedReader(reader);

            String line;
            for (; ; ) {
                line = br.readLine();
                if (line == null)
                    break;
                parseVpnLine(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        group.shutdownGracefully();

    }

    private static EventLoopGroup group = new NioEventLoopGroup();
    static Charset utf8 = Charset.forName("utf-8");

    private static void parseVpnLine(final String line) {
        final long curr = System.currentTimeMillis();

        String[] split = line.split(",");
        if (split.length < 2) return;
        String host = split[1];
        if ("IP".equals(host)) return;
        int port = 995;

        String s = split[14];
        if (s.isEmpty()) return;  // No ovpn file

        ByteBuf decode = Base64.decode(Unpooled.wrappedBuffer(s.getBytes()));
        String ovpn = decode.toString(utf8);
        for (String o : ovpn.split("\r\n")) {
            if (!o.startsWith("remote")) continue;
            String[] strings = o.split(" ");
            if (strings.length >= 3) {
                host = strings[1];
                port = Integer.valueOf(strings[2]);
            }
        }

        // create tcp/ip connector
        Bootstrap b = new Bootstrap();
        String finalHost = host;
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel channel) throws Exception {
                        channel.pipeline().addLast("handle", new ChannelInboundHandlerAdapter() {
                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                super.exceptionCaught(ctx, cause);
                            }

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                super.channelActive(ctx);

                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                super.channelInactive(ctx);
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
                            }
                        });
                    }
                });

        ChannelFuture f;
        try {
            String country = split[5];
            System.out.println("Trying " + split[0] + " \tIP: " + finalHost + " \tPort:" + port + " \tCountry:" + country);

            b.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000);
            f = b.connect(host, port);
            ChannelFuture await = f.await();
            if (await.isDone() && await.isSuccess()) {
                long l = System.currentTimeMillis() - curr;

                if (!country.isEmpty()) {
                    File dir = new File(country);
                    dir.mkdir();
                }
                System.out.println("\t" + split[0] + " \tIP: " + finalHost + " \tPort:" + port + " \tCountry:" + country + " \tMS:" + l);

                // #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
                String x = l + "_" + split[0];
                String filename = country.isEmpty() ? x : country + File.separator + x;
                File n = new File(filename + ".ovpn");
                FileWriter writer = new FileWriter(n);
                writer.write(ovpn);
                writer.write("\r\n");
                if (!add.isEmpty()) writer.write(add);
                writer.flush();
                writer.close();
            }

            f.channel().close();
        } catch (Exception ignored) {

        }
    }
}

