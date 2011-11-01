/*
 * Copyright 2010 Joel Hockey (joel.hockey@gmail.com).  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 * THIS SOURCE CODE IS PROVIDED BY JOEL HOCKEY WITH A 30-DAY MONEY BACK
 * GUARANTEE.  IF THIS CODE DOES NOT MEAN WHAT IT SAYS IT MEANS WITHIN THE
 * FIRST 30 DAYS, SIMPLY RETURN THIS CODE IN ORIGINAL CONDITION FOR A PARTIAL
 * REFUND.  IN ADDITION, I WILL REFORMAT THIS CODE USING YOUR PREFERRED
 * BRACE-POSITIONING AND INDENTATION.  THIS WARRANTY IS VOID IF THE CODE IS
 * FOUND TO HAVE BEEN COMPILED.  NO FURTHER WARRANTY IS OFFERED.
 */
package net.java.jless.iso24727;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.jless.codec.Hex;
import net.java.jless.codec.TLV;
import net.java.jless.iso24727.ISO24727.ACLListReturn;
import net.java.jless.iso24727.ISO24727.CardApplicationConnectReturn;
import net.java.jless.iso24727.ISO24727.CardApplicationListReturn;
import net.java.jless.iso24727.ISO24727.DIDAuthenticateReturn;
import net.java.jless.iso24727.ISO24727.DIDGetReturn;
import net.java.jless.iso24727.ISO24727.DSIReadReturn;
import net.java.jless.iso24727.ISO24727.DataSetSelectReturn;
import net.java.jless.smartcard.APDURes;
import net.java.jless.smartcard.Smartcard;
import net.java.jless.smartcard.SmartcardException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class ISO24727Smartcard {
    private static final Log log = LogFactory.getLog(ISO24727Smartcard.class);
    public static final String AID_ALPHA = ISO24727.AID_ALPHA;
    public static final int SW_9000_OK = 0x9000;
    private Smartcard card;
    private int cla = 0x00;
    private int ins = 0xc2;
    private boolean secureOn = false;
    private SecureMessaging secure;
    private Map<String, byte[]> cnxnHandles = new HashMap<String, byte[]>();
    private String currentAid = "";
    private String currentDataSet = "";

    public ISO24727Smartcard(Smartcard card) {
        this.card = card;
    }

    private byte[] cnxnHandle() { return cnxnHandles.get(currentAid); }

    public void cardApplicationConnect(String aid) throws SmartcardException, ISO24727Exception, SecureMessagingException {
        byte[] aidbuf = Hex.s2b(aid);
        if (currentAid.equals(Hex.B2S(aidbuf))) {
            return;
        }
        TLV req = ISO24727.cardApplicationConnect(card.getIFDName(), aidbuf);
        byte[] res = transmit(req, "CardApplicationConnect", aid);
        CardApplicationConnectReturn isoReturn = new CardApplicationConnectReturn(res);
        currentAid = Hex.B2S(aidbuf);
        cnxnHandles.put(currentAid, isoReturn.getConnectionHandle());
    }

    public List<byte[]> cardApplicationList() throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(AID_ALPHA);
        TLV req = ISO24727.cardApplicationList(cnxnHandle());
        byte[] res = transmit(req, "CardApplicationList", null);
        CardApplicationListReturn isoReturn = new CardApplicationListReturn(res);
        return isoReturn.getCardApplicationNameList();
    }

    public TLV didGet(int didScope, String didName) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(AID_ALPHA);
        TLV req = ISO24727.didGet(cnxnHandle(), didScope, didName);
        byte[] res = transmit(req, "DIDGet", didScope + ":" + didName);
        DIDGetReturn isoReturn = new DIDGetReturn(res);
        return isoReturn.getMarker();
    }

    public TLV didAuthenticate(int didScope, String didName, byte[] didAuthData) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        TLV req = ISO24727.didAuthenticate(cnxnHandle(), didScope, didName, didAuthData);
        String param = null;
        if (log.isDebugEnabled()) {
            param = didScope + ":" + didName + ":" + Hex.b2s(didAuthData);
        }
        byte[] res = transmit(req, "DIDAuthenticate", param);
        DIDAuthenticateReturn isoReturn = new DIDAuthenticateReturn(res);
        return isoReturn.getAuthProtocolData();
    }

    public void dataSetSelect(String aid, String dataSetName) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(aid);
        if (currentDataSet.equals(dataSetName)) {
            return;
        }
        TLV req = ISO24727.dataSetSelect(cnxnHandle(), dataSetName);
        byte[] res = transmit(req, "DataSetSelect", aid + ":" + dataSetName);
        DataSetSelectReturn isoReturn = new DataSetSelectReturn(res);
        currentDataSet = dataSetName;
    }

    public TLV dsiRead(String aid, String dataSetName, String dsiName) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        dataSetSelect(aid, dataSetName);
        TLV req = ISO24727.dsiRead(cnxnHandle(), dsiName);
        byte[] res = transmit(req, "DSIRead", aid + ":" + dataSetName + ":" + dsiName);
        DSIReadReturn isoReturn = new DSIReadReturn(res);
        return isoReturn.getDsi();
    }

    public TLV aclListApp(String aid) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(AID_ALPHA);
        TLV req = ISO24727.aclListApp(cnxnHandle(), Hex.s2b(aid));
        byte[] res = transmit(req, "ACLList", "AID " + aid);
        ACLListReturn isoReturn = new ACLListReturn(res);
        return isoReturn.getAcl();
    }

    public TLV aclListDID(String aid, String didName) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(aid);
        TLV req = ISO24727.aclListDID(cnxnHandle(), didName);
        byte[] res = transmit(req, "ACLList", "DID " + didName);
        ACLListReturn isoReturn = new ACLListReturn(res);
        return isoReturn.getAcl();
    }

    public TLV aclListDataSet(String aid, String dataSetName) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        cardApplicationConnect(aid);
        TLV req = ISO24727.aclListDataSet(cnxnHandle(), dataSetName);
        byte[] res = transmit(req, "ACLList", "DataSet " + aid + ":" + dataSetName);
        ACLListReturn isoReturn = new ACLListReturn(res);
        return isoReturn.getAcl();
    }

    private byte[] transmit(TLV req, String action, String params) throws ISO24727Exception, SmartcardException, SecureMessagingException {
        if (log.isInfoEnabled()) {
            if (params != null) {
                action += " " + params;
            }
            log.info("> " + action);
        }
        if (log.isDebugEnabled()) {
            log.debug("\n" + req.dump());
        }
        long start = System.currentTimeMillis();

        byte[] encoded = req.encode();
        if (secureOn) {
            encoded = secure.encryptAndMac(encoded);
        }
        APDURes res = card.transmit(cla, ins, 0, 0, encoded, 0);
        if (res.getSW() != SW_9000_OK) {
            throw new ISO24727Exception("Error expected SW=0x9000, got apdu: " + Hex.b2s(res.getBytes()), action, res.getSW(), null);
        }
        long end = java.lang.System.currentTimeMillis();
        encoded = res.getData();
        if (log.isInfoEnabled()) {
            log.info("< " + action + " (" + (end-start) + ") " + Hex.b2s(encoded));
        }
        if (secureOn) {
            encoded = secure.decryptAndValidateMac(encoded);
        }
        return encoded;
    }
}