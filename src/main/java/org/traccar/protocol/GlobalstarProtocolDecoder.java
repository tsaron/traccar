/*
 * Copyright 2019 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.traccar.BaseHttpProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.Protocol;
import org.traccar.helper.DataConverter;
import org.traccar.model.Position;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.net.SocketAddress;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class GlobalstarProtocolDecoder extends BaseHttpProtocolDecoder {

    private DocumentBuilder documentBuilder;
    private XPath xPath;
    private XPathExpression messageExpression;

    public GlobalstarProtocolDecoder(Protocol protocol) {
        super(protocol);
        try {
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            builderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            builderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            builderFactory.setXIncludeAware(false);
            builderFactory.setExpandEntityReferences(false);
            documentBuilder = builderFactory.newDocumentBuilder();
            xPath = XPathFactory.newInstance().newXPath();
            messageExpression = xPath.compile("//stuMessages/stuMessage");
        } catch (ParserConfigurationException | XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        FullHttpRequest request = (FullHttpRequest) msg;

        Document document = documentBuilder.parse(new ByteBufferBackedInputStream(request.content().nioBuffer()));
        NodeList nodes = (NodeList) messageExpression.evaluate(document, XPathConstants.NODESET);

        List<Position> positions = new LinkedList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, xPath.evaluate("esn", node));
            if (deviceSession != null) {

                Position position = new Position(getProtocolName());
                position.setDeviceId(deviceSession.getDeviceId());

                position.setValid(true);
                position.setTime(new Date(Long.parseLong(xPath.evaluate("unixTime", node)) * 1000));

                ByteBuf buf = Unpooled.wrappedBuffer(
                        DataConverter.parseHex(xPath.evaluate("payload", node).substring(2)));

                buf.readUnsignedByte(); // flags

                position.setLatitude(buf.readUnsignedMedium() * 90.0 / (1 << 23));
                if (position.getLatitude() > 90) {
                    position.setLatitude(position.getLatitude() - 180);
                }

                position.setLongitude(buf.readUnsignedMedium() * 180.0 / (1 << 23));
                if (position.getLongitude() > 180) {
                    position.setLongitude(position.getLongitude() - 360);
                }

                buf.readUnsignedByte(); // status
                buf.readUnsignedByte(); // status

                positions.add(position);

            }
        }

        sendResponse(channel, HttpResponseStatus.OK);
        return positions;
    }

}
