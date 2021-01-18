package org.yangxin.websocket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;

/**
 * 接收/处理/响应客户端websocket请求的核心业务处理类
 *
 * @author yangxin
 * 2020/06/01 20:18
 */
@SuppressWarnings("unused")
@Slf4j
public class WebSocketHandler extends SimpleChannelInboundHandler<Object> {

    private WebSocketServerHandshaker webSocketServerHandshaker;

    private static final String WEB_SOCKET_URL = "ws://localhost:8888/websocket";

    /**
     * 服务端处理客户端WebSocket请求的核心方法
     */
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object o) {
        // 处理客户端向服务器端发起http握手请求的业务
        if (o instanceof FullHttpRequest) {
            handHttpRequest(channelHandlerContext, (FullHttpRequest) o);
        } else if (o instanceof WebSocketFrame) {
            // 处理websocket连接业务
            handWebsocketFrame(channelHandlerContext, (WebSocketFrame) o);
        }
    }

    /**
     * 处理websocket请求
     */
    private void handWebsocketFrame(ChannelHandlerContext channelHandlerContext, WebSocketFrame webSocketFrame) {
        // 判断是否是关闭websocket的指令
        if (webSocketFrame instanceof CloseWebSocketFrame) {
            webSocketServerHandshaker.close(channelHandlerContext.channel(),
                    (CloseWebSocketFrame) webSocketFrame.retain());
        }
        // 判断是否是ping消息
        if (webSocketFrame instanceof PingWebSocketFrame) {
            channelHandlerContext.channel().write(new PongWebSocketFrame(webSocketFrame.content().retain()));
            return;
        }
        // 判断是否是二进制消息，如果是二进制消息，抛出异常
        if (!(webSocketFrame instanceof TextWebSocketFrame)) {
            log.info("目前我们不支持二进制消息");
            throw new RuntimeException("【" + this.getClass().getName() + "】不支持消息");
        }

        // 返回应答消息
        // 获取客户端向服务端发送的消息
        String request = ((TextWebSocketFrame) webSocketFrame).text();
        log.info("服务端收到客户端的消息: [{}]", request);
        TextWebSocketFrame textWebSocketFrame = new TextWebSocketFrame(new Date().toString()
                + channelHandlerContext.channel().id() + "===>>>" + request);
        // 群发，服务端向每个连接上来的客户端群发消息
        NettyConfig.channelGroup.writeAndFlush(textWebSocketFrame);
    }

    /**
     * 处理客户端向服务端发起http握手请求的业务
     */
    private void handHttpRequest(ChannelHandlerContext channelHandlerContext, FullHttpRequest fullHttpRequest) {
        // 如果解码失败，或者协议头的字段upgrade的字段值不为websocket，则返回HTTP响应，调用结束
        if (!fullHttpRequest.decoderResult().isSuccess()
                || !("websocket").equals(fullHttpRequest.headers().get("Upgrade"))) {
            sendHttpResponse(channelHandlerContext, fullHttpRequest, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                    HttpResponseStatus.BAD_REQUEST));
            return;
        }

        // 工厂模式创建websocket握手者
        WebSocketServerHandshakerFactory webSocketServerHandshakerFactory
                = new WebSocketServerHandshakerFactory(WEB_SOCKET_URL, null, false);
        webSocketServerHandshaker = webSocketServerHandshakerFactory
                .newHandshaker(fullHttpRequest);
        // 握手者为空，则发送不支持的版本的响应
        if (webSocketServerHandshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(channelHandlerContext.channel());
        } else {
            // 握手者不为空，则握手
            webSocketServerHandshaker.handshake(channelHandlerContext.channel(), fullHttpRequest);
        }
    }

    /**
     * 服务端向客户端响应消息
     */
    private void sendHttpResponse(ChannelHandlerContext channelHandlerContext,
                                  FullHttpRequest fullHttpRequest,
                                  DefaultFullHttpResponse defaultFullHttpResponse) {
        if (defaultFullHttpResponse.status().code() != 200) {
            ByteBuf byteBuf = Unpooled.copiedBuffer(defaultFullHttpResponse.status().toString(), CharsetUtil.UTF_8);
            defaultFullHttpResponse.content().writeBytes(byteBuf);
        }

        // 服务端向客户端发送数据
        ChannelFuture channelFuture = channelHandlerContext.channel().writeAndFlush(defaultFullHttpResponse);
        if (defaultFullHttpResponse.status().code() != 200) {
            channelFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    /**
     * 客户端与服务端创建连接的时候调用
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        NettyConfig.channelGroup.add(ctx.channel());
        log.info("客户端与服务端连接开启。");
    }

    /**
     * 客户端与服务端断开连接的时候调用
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        NettyConfig.channelGroup.remove(ctx.channel());
        log.info("客户端与服务端连接关闭……");
    }

    /**
     * 服务端接收客户端发送过来的数据结束之后调用
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    /**
     * 工程出现异常的时候调用
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
