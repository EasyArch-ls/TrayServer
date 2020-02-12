package netty;

import com.alibaba.fastjson.JSON;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MyWebSocketServerHandler extends
        SimpleChannelInboundHandler<Object> {

    private static final Logger logger = Logger
            .getLogger(WebSocketServerHandshaker.class.getName());
    private WebSocketServerHandshaker handshaker;
    private static HashMap<String, ChannelId> hashMap=new HashMap();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 添加
        Global.group.add(ctx.channel());
        System.out.println("客户端与服务端连接开启");

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // 移除
        Global.group.remove(ctx.channel());
        System.out.println("客户端与服务端连接关闭");

    }



    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx,
                                       WebSocketFrame frame) {
        // 判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame
                    .retain());
        }

        // 判断是否ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(
                    new PongWebSocketFrame(frame.content().retain()));
            return;
        }

        // 本例程仅支持文本消息，不支持二进制消息
        if (!(frame instanceof TextWebSocketFrame)) {

            System.out.println("本例程仅支持文本消息，不支持二进制消息");

            throw new UnsupportedOperationException(String.format(
                    "%s frame types not supported", frame.getClass().getName()));
        }

        // 返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        HashMap map=new HashMap();
        map= JSON.parseObject(request,HashMap.class);
        System.out.println("服务端收到：" + request);
        if(!map.get("type").equals("0")){
            System.out.println(map.get("toUser"));
            TextWebSocketFrame tws = new TextWebSocketFrame( "单聊：" + map.get("msg"));
            Global.group.find(hashMap.get(map.get("toUser"))).writeAndFlush(tws);
            TextWebSocketFrame tws1 = new TextWebSocketFrame("你向发送了：" + map.get("msg"));
            ctx.channel().writeAndFlush(tws1);
        }else {
            TextWebSocketFrame tws = new TextWebSocketFrame(
                     ctx.channel().id() + "：" + request);
            // 群发
            Global.group.writeAndFlush(tws);
        }

    }

    private void handleHttpRequest(ChannelHandlerContext ctx,
                                   FullHttpRequest fuHr) throws UnsupportedEncodingException {

        if (!fuHr.decoderResult().isSuccess()
                || (!"websocket".equals(fuHr.headers().get("Upgrade")))) {
            sendHttpResponse(ctx, fuHr, new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://0.0.0.0:8080", null, false);
        String url = fuHr.uri().split("/")[2];
        url=URLDecoder.decode(url,"utf-8");
        logger.info("URL: "+url);
        System.out.println(url);
        hashMap.put(url,ctx.channel().id());
        handshaker = wsFactory.newHandshaker(fuHr);
        ByteBuf byteBuf=fuHr.content();//shuju
        String data=byteBuf.toString(Charset.forName("utf-8"));
        if (handshaker == null) {
            WebSocketServerHandshakerFactory
                    .sendUnsupportedVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), fuHr);
        }

    }

    private static void sendHttpResponse(ChannelHandlerContext ctx,
                                         FullHttpRequest req, DefaultFullHttpResponse res) {

        // 返回应答给客户端
        if (res.status().code() != 200) {
            ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),
                    CharsetUtil.UTF_8);
            res.content().writeBytes(buf);
            buf.release();
        }

        // 如果是非Keep-Alive，关闭连接
        ChannelFuture f = ctx.channel().writeAndFlush(res);
        if (!isKeepAlive(req) || res.status().code() != 200) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static boolean isKeepAlive(FullHttpRequest req) {

        return false;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {

        cause.printStackTrace();
        ctx.close();

    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object o) throws Exception {

        if (o instanceof FullHttpRequest) {

            handleHttpRequest(ctx, ((FullHttpRequest) o));

        } else if (o instanceof WebSocketFrame) {

            handlerWebSocketFrame(ctx, (WebSocketFrame) o);

        }
    }
}
