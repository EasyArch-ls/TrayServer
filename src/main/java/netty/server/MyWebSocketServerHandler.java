package netty.server;

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
import netty.server.Global;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.logging.Logger;

public class MyWebSocketServerHandler extends
        SimpleChannelInboundHandler<Object>  {

    private static final Logger logger = Logger
            .getLogger(WebSocketServerHandshaker.class.getName());
    private WebSocketServerHandshaker handshaker;
    private static HashMap<String, String> hashMap=new HashMap();
    private static HashMap<String,ChannelId> hashMap1=new HashMap<>();
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

        // 返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        System.out.println("服务端收到：" + request);
        HashMap map=null;
        map= JSON.parseObject(request,HashMap.class);
        if(!map.get("type").equals("0")){
            System.out.println(map.get("toUser"));
            TextWebSocketFrame tws = new TextWebSocketFrame("单聊：" + map.get("msg"));
            Global.group.find(hashMap1.get(hashMap.get(map.get("toUser")))).writeAndFlush(tws);
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
            String url = fuHr.uri().split("/")[2];
            url=URLDecoder.decode(url,"utf-8");
            logger.info("URL: "+url);
            InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
            String clientIP = insocket.getAddress().getHostAddress();
            hashMap.put(url,clientIP);
            ByteBuf byteBuf=fuHr.content();//shuju
            handlerWebSocketFrame(ctx,new TextWebSocketFrame(byteBuf));
            return;
        }

        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://0.0.0.0:8080/websocket", null, false);
        String url = fuHr.uri().split("/")[2];
        url=URLDecoder.decode(url,"utf-8");
        logger.info("URL: "+url);
        System.out.println(url);
        InetSocketAddress insocket = (InetSocketAddress) ctx.channel().remoteAddress();
        String clientIP = insocket.getAddress().getHostAddress();
        hashMap1.put(clientIP,ctx.channel().id());
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
            //ctx.channel().writeAndFlush("aaaaa");

        } else if (o instanceof WebSocketFrame) {

            handlerWebSocketFrame(ctx, (WebSocketFrame) o);
        }
    }


}
