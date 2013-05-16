/**
 * $Id: RemotingHelper.java 1831 2013-05-16 01:39:51Z shijia.wxr $
 */
package com.alibaba.rocketmq.remoting.common;

import io.netty.channel.Channel;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import com.google.protobuf.InvalidProtocolBufferException;
import com.alibaba.rocketmq.remoting.exception.RemotingConnectException;
import com.alibaba.rocketmq.remoting.exception.RemotingSendRequestException;
import com.alibaba.rocketmq.remoting.exception.RemotingTimeoutException;
import com.alibaba.rocketmq.remoting.protocol.RemotingCommand;
import com.alibaba.rocketmq.remoting.protocol.RemotingProtos.KVPair;
import com.alibaba.rocketmq.remoting.protocol.RemotingProtos.KVPairList;
import com.alibaba.rocketmq.remoting.protocol.RemotingProtos.NVPair;
import com.alibaba.rocketmq.remoting.protocol.RemotingProtos.NVPairList;
import com.alibaba.rocketmq.remoting.protocol.RemotingProtos.StringList;


/**
 * @author vintage.wang@gmail.com shijia.wxr@taobao.com
 * 
 */
public class RemotingHelper {
    public static final String RemotingLogName = "MetaqRemoting";


    /**
     * IP:PORT
     */
    public static SocketAddress string2SocketAddress(final String addr) {
        String[] s = addr.split(":");
        InetSocketAddress isa = new InetSocketAddress(s[0], Integer.valueOf(s[1]));
        return isa;
    }


    /**
     * ���л��ַ����б�
     */
    public static byte[] stringList2Bytes(final List<String> strs) {
        if (null == strs || strs.isEmpty()) {
            return null;
        }

        StringList.Builder builder = StringList.newBuilder();

        for (String str : strs) {
            builder.addName(str);
        }

        return builder.build().toByteArray();
    }


    /**
     * �����л��ַ����б�
     */
    public static List<String> bytes2StringList(final byte[] data) throws InvalidProtocolBufferException {
        if (null == data) {
            return null;
        }
        StringList stringList = StringList.parseFrom(data);
        return stringList.getNameList();
    }


    /**
     * ���л���ֵ��
     */
    public static byte[] hashMap2Bytes(final HashMap<Integer/* key */, String/* value */> nms) {
        if (null == nms || nms.isEmpty()) {
            return null;
        }

        KVPairList.Builder builder = KVPairList.newBuilder();

        Iterator<Entry<Integer, String>> it = nms.entrySet().iterator();
        for (int index = 0; it.hasNext(); index++) {
            Entry<Integer, String> entry = (Entry<Integer, String>) it.next();
            int key = entry.getKey();
            String val = entry.getValue();

            KVPair.Builder kvb = KVPair.newBuilder();
            kvb.setKey(key);
            kvb.setValue(val);
            builder.addFields(index, kvb.build());
        }

        return builder.build().toByteArray();
    }


    /**
     * �����л���ֵ��
     * 
     * @throws InvalidProtocolBufferException
     */
    public static HashMap<Integer/* key */, String/* value */> bytes2HashMap(final byte[] data)
            throws InvalidProtocolBufferException {
        if (null == data) {
            return null;
        }

        HashMap<Integer/* key */, String/* value */> result = new HashMap<Integer/* key */, String/* value */>();

        KVPairList kvList = KVPairList.parseFrom(data);

        List<KVPair> kvList2 = kvList.getFieldsList();

        for (KVPair kv : kvList2) {
            result.put(kv.getKey(), kv.getValue());
        }

        return result;
    }


    /**
     * ���л���ֵ��
     */
    public static byte[] hashMapString2Bytes(final HashMap<String/* name */, String/* value */> nms) {
        if (null == nms || nms.isEmpty()) {
            return null;
        }

        NVPairList.Builder builder = NVPairList.newBuilder();

        Iterator<Entry<String, String>> it = nms.entrySet().iterator();
        for (int index = 0; it.hasNext(); index++) {
            Entry<String, String> entry = (Entry<String, String>) it.next();
            String key = entry.getKey();
            String val = entry.getValue();

            NVPair.Builder kvb = NVPair.newBuilder();
            kvb.setName(key);
            kvb.setValue(val);
            builder.addFields(index, kvb.build());
        }

        return builder.build().toByteArray();
    }


    /**
     * ���л���ֵ��
     */
    public static byte[] properties2Bytes(final Properties nms) {
        if (null == nms || nms.isEmpty()) {
            return null;
        }

        NVPairList.Builder builder = NVPairList.newBuilder();

        Set<Object> keyset = nms.keySet();
        int index = 0;
        for (Object object : keyset) {
            String key = object.toString();
            String val = nms.getProperty(key);

            NVPair.Builder kvb = NVPair.newBuilder();
            kvb.setName(key);
            kvb.setValue(val);
            builder.addFields(index++, kvb.build());
        }

        return builder.build().toByteArray();
    }


    /**
     * �����л���ֵ��
     * 
     * @throws InvalidProtocolBufferException
     */
    public static HashMap<String/* name */, String/* value */> bytes2HashMapString(final byte[] data)
            throws InvalidProtocolBufferException {
        if (null == data) {
            return null;
        }

        HashMap<String/* name */, String/* value */> result = new HashMap<String/* name */, String/* value */>();

        NVPairList ps = NVPairList.parseFrom(data);

        List<NVPair> ps2 = ps.getFieldsList();

        for (NVPair kv : ps2) {
            result.put(kv.getName(), kv.getValue());
        }

        return result;
    }


    /**
     * �����ӵ��� TODO
     */
    public static RemotingCommand invokeSync(final String addr, final RemotingCommand request,
            final long timeoutMillis) throws InterruptedException, RemotingConnectException,
            RemotingSendRequestException, RemotingTimeoutException {
        long beginTime = System.currentTimeMillis();
        SocketAddress socketAddress = RemotingUtil.string2SocketAddress(addr);
        SocketChannel socketChannel = RemotingUtil.connect(socketAddress);
        if (socketChannel != null) {
            boolean sendRequestOK = false;

            try {
                // ʹ������ģʽ
                socketChannel.configureBlocking(true);
                /*
                 * FIXME The read methods in SocketChannel (and DatagramChannel)
                 * do notsupport timeouts
                 * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4614802
                 */
                socketChannel.socket().setSoTimeout((int) timeoutMillis);

                // ��������
                ByteBuffer byteBufferRequest = request.encode();
                while (byteBufferRequest.hasRemaining()) {
                    int length = socketChannel.write(byteBufferRequest);
                    if (length > 0) {
                        if (byteBufferRequest.hasRemaining()) {
                            if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
                                // ��������ʱ
                                throw new RemotingSendRequestException(addr);
                            }
                        }
                    }
                    else {
                        throw new RemotingSendRequestException(addr);
                    }

                    // �Ƚ���
                    Thread.sleep(1);
                }

                sendRequestOK = true;

                // ����Ӧ�� SIZE
                ByteBuffer byteBufferSize = ByteBuffer.allocate(4);
                while (byteBufferSize.hasRemaining()) {
                    int length = socketChannel.read(byteBufferSize);
                    if (length > 0) {
                        if (byteBufferSize.hasRemaining()) {
                            if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
                                // ����Ӧ��ʱ
                                throw new RemotingTimeoutException(addr, timeoutMillis);
                            }
                        }
                    }
                    else {
                        throw new RemotingTimeoutException(addr, timeoutMillis);
                    }

                    // �Ƚ���
                    Thread.sleep(1);
                }

                // ����Ӧ�� BODY
                int size = byteBufferSize.getInt(0);
                ByteBuffer byteBufferBody = ByteBuffer.allocate(size);
                while (byteBufferBody.hasRemaining()) {
                    int length = socketChannel.read(byteBufferBody);
                    if (length > 0) {
                        if (byteBufferBody.hasRemaining()) {
                            if ((System.currentTimeMillis() - beginTime) > timeoutMillis) {
                                // ����Ӧ��ʱ
                                throw new RemotingTimeoutException(addr, timeoutMillis);
                            }
                        }
                    }
                    else {
                        throw new RemotingTimeoutException(addr, timeoutMillis);
                    }

                    // �Ƚ���
                    Thread.sleep(1);
                }

                // ��Ӧ�����ݽ���
                byteBufferBody.flip();
                return RemotingCommand.decode(byteBufferBody);
            }
            catch (IOException e) {
                e.printStackTrace();

                if (sendRequestOK) {
                    throw new RemotingTimeoutException(addr, timeoutMillis);
                }
                else {
                    throw new RemotingSendRequestException(addr);
                }
            }
            finally {
                try {
                    socketChannel.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else {
            throw new RemotingConnectException(addr);
        }
    }


    public static String parseChannelRemoteAddr(final Channel channel) {
        final SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";

        if (addr.length() > 0) {
            return addr.substring(1);
        }

        return "";
    }
}