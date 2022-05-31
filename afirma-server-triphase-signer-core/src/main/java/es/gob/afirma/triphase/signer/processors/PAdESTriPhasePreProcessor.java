/* Copyright (C) 2011 [Gobierno de Espana]
 * This file is part of "Cliente @Firma".
 * "Cliente @Firma" is free software; you can redistribute it and/or modify it under the terms of:
 *   - the GNU General Public License as published by the Free Software Foundation;
 *     either version 2 of the License, or (at your option) any later version.
 *   - or The European Software License; either version 1.1 or (at your option) any later version.
 * You may contact the copyright holder at: soporte.afirma@seap.minhap.es
 */

package es.gob.afirma.triphase.signer.processors;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import es.gob.afirma.core.AOException;
import es.gob.afirma.core.misc.Base64;
import es.gob.afirma.core.misc.protocol.ConfirmationNeededException;
import es.gob.afirma.core.signers.AOSignConstants;
import es.gob.afirma.core.signers.CounterSignTarget;
import es.gob.afirma.core.signers.TriphaseData;
import es.gob.afirma.core.signers.TriphaseData.TriSign;
import es.gob.afirma.signers.pades.AOPDFSigner;
import es.gob.afirma.signers.pades.InvalidPdfException;
import es.gob.afirma.signers.pades.PAdESTriPhaseSigner;
import es.gob.afirma.signers.pades.PdfExtraParams;
import es.gob.afirma.signers.pades.PdfSignResult;
import es.gob.afirma.signvalidation.InvalidSignatureException;
import es.gob.afirma.signvalidation.SignValidity;
import es.gob.afirma.signvalidation.SignValidity.SIGN_DETAIL_TYPE;
import es.gob.afirma.signvalidation.ValidatePdfSignature;

/** Procesador de firmas trif&aacute;sicas PAdES.
 * @author Tom&aacute;s Garc&iacute;a Mer&aacute;s. */
public final class PAdESTriPhasePreProcessor implements TriPhasePreProcessor {

	/** Momento de la firma, establecido en el servidor. */
	private static final String PROPERTY_NAME_SIGN_TIME = "TIME"; //$NON-NLS-1$

	/** Identificador interno del PDF. */
	private static final String PROPERTY_NAME_PDF_UNIQUE_ID = "PID"; //$NON-NLS-1$

	/** Prefijo para cada prefirma. */
	private static final String PROPERTY_NAME_PRESIGN = "PRE"; //$NON-NLS-1$

	/** Firma PKCS#1. */
	private static final String PROPERTY_NAME_PKCS1_SIGN = "PK1"; //$NON-NLS-1$

	/** Indica si la postfirma requiere la prefirma. */
	private static final String PROPERTY_NAME_NEED_PRE = "NEED_PRE"; //$NON-NLS-1$

	/** Manejador de registro. */
	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Numero maximo de paginas en las que comprobar un posible Pdf Shadow Attack. */
	private String maxPagesToCheckPSA;

	/** Indica el numero de p&aacute;ginas para comprobar posible Pdf Shadow Attack. */
	private String pagesToCheckShadowAttack;

	/** Indica si se permite o no la comprobacion de posibles Pdf Shadow Attacks. */
	private String allowPdfShadowAttack;

	@Override
	public TriphaseData preProcessPreSign(final byte[] data,
			                        final String algorithm,
			                        final X509Certificate[] cert,
			                        final Properties extraParams,
			                        final boolean checkSignatures) throws IOException,
			                                                             AOException{
		LOGGER.info("Prefirma PAdES - Firma - INICIO"); //$NON-NLS-1$

		// Comprobamos la validez de la firma de entrada si se solicito
        if (checkSignatures && new AOPDFSigner().isSign(data)) {
			String pagesToCheckPSA = this.maxPagesToCheckPSA;

			if (this.pagesToCheckShadowAttack != null && !PdfExtraParams.PAGES_TO_CHECK_PSA_VALUE_ALL.equals(this.pagesToCheckShadowAttack)
					&& this.maxPagesToCheckPSA != null && !PdfExtraParams.PAGES_TO_CHECK_PSA_VALUE_ALL.equals(this.maxPagesToCheckPSA)) {
				final int maxPagesInt = Integer.parseInt(this.maxPagesToCheckPSA);
				final int pagesToCheckInt = Integer.parseInt(this.pagesToCheckShadowAttack);
				pagesToCheckPSA = pagesToCheckInt < maxPagesInt ? this.pagesToCheckShadowAttack : this.maxPagesToCheckPSA;
			}

			extraParams.put(PdfExtraParams.ALLOW_SHADOW_ATTACK, this.allowPdfShadowAttack);
			extraParams.put(PdfExtraParams.PAGES_TO_CHECK_PSA, pagesToCheckPSA);
			extraParams.put(PdfExtraParams.CHECK_CERTIFICATES, PdfExtraParams.CHECK_CERTIFICATES_VALUE_TRUE);
			SignValidity validity;

			try {
				validity = new ValidatePdfSignature().validate(data, extraParams);
	        	if (validity.getValidity() == SIGN_DETAIL_TYPE.KO) {
	        		throw new InvalidSignatureException("Se encontraron firmas no validas en el PDF: " + validity.getError().toString()); //$NON-NLS-1$
	        	}
			} catch (final ConfirmationNeededException e) {
				// No se hace nada
			} catch (final IOException e) {
				LOGGER.severe("Error al validar documento: " + e); //$NON-NLS-1$
				throw e;
			}
        }

		final GregorianCalendar signTime = new GregorianCalendar();

		// Primera fase (servidor)
		LOGGER.info("Se invocan las funciones internas de prefirma PAdES"); //$NON-NLS-1$
		final PdfSignResult preSignature;
		try {
			preSignature = PAdESTriPhaseSigner.preSign(
				AOSignConstants.getDigestAlgorithmName(algorithm),
				data,
				cert,
				signTime,
				extraParams,
				true
			);
		}
		catch (final InvalidPdfException e) {
			LOGGER.severe("El documento no es un PDF y no se puede firmar: " + e); //$NON-NLS-1$
			throw e;
		}

		LOGGER.info("Se prepara la respuesta de la prefirma PAdES"); //$NON-NLS-1$

		final TriphaseData triphaseData = new TriphaseData();

		// Ahora pasamos al cliente:
		// 1.- La prefirma para que haga el PKCS#1
		// 2.- La fecha generada en el servidor para reutilizarla en la postfirma
		// 3.- El ID de PDF para reutilizarlo en la postfirma

		final Map<String, String> signConfig = new HashMap<>();
		signConfig.put(PROPERTY_NAME_PRESIGN, Base64.encode(preSignature.getSign()));
		signConfig.put(PROPERTY_NAME_NEED_PRE, Boolean.TRUE.toString());
		signConfig.put(PROPERTY_NAME_SIGN_TIME, Long.toString(signTime.getTimeInMillis()));
		signConfig.put(PROPERTY_NAME_PDF_UNIQUE_ID, Base64.encode(preSignature.getFileID().getBytes()));

		triphaseData.addSignOperation(
			new TriSign(
				signConfig,
				TriPhaseUtil.getSignatureId(extraParams)
			)
		);

		LOGGER.info("Prefirma PAdES - Firma - FIN"); //$NON-NLS-1$

		return triphaseData;
	}

	@Override
	public byte[] preProcessPostSign(final byte[] docBytes,
			                         final String algorithm,
			                         final X509Certificate[] cert,
			                         final Properties extraParams,
			                         final byte[] session) throws NoSuchAlgorithmException,
			                                                      AOException,
			                                                      IOException {

		return preProcessPostSign(docBytes, algorithm, cert, extraParams, TriphaseData.parser(session));
	}

	@Override
	public byte[] preProcessPostSign(final byte[] docBytes,
			                         final String signatureAlgorithm,
			                         final X509Certificate[] cert,
			                         final Properties extraParams,
			                         final TriphaseData triphaseData) throws NoSuchAlgorithmException,
			                                                                 AOException,
			                                                                 IOException {

		LOGGER.info("Postfirma PAdES - Firma - INICIO"); //$NON-NLS-1$

		// Cargamos la configuracion de la operacion
		if (triphaseData.getSignsCount() < 1) {
			LOGGER.severe("No se ha encontrado la informacion de firma en la peticion"); //$NON-NLS-1$
			throw new AOException("No se ha encontrado la informacion de firma en la peticion"); //$NON-NLS-1$
		}

		final TriSign signConfig = triphaseData.getSign(0);

		// Comprobamos que se incluyan todos los parametros necesarios para la postfirma
		checkSession(signConfig);

		// Preparo la fecha de firma
		final GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance();

		try {
			cal.setTimeInMillis(Long.parseLong(signConfig.getProperty(PROPERTY_NAME_SIGN_TIME)));
		}
		catch (final Exception e) {
			LOGGER.warning("La hora de firma indicada no es valida: " + e.toString()); //$NON-NLS-1$
		}

		// Ya con todos los datos hacemos la postfirma
		final PdfSignResult signResult = new PdfSignResult(
			new String(Base64.decode(signConfig.getProperty(PROPERTY_NAME_PDF_UNIQUE_ID))),
			Base64.decode(signConfig.getProperty(PROPERTY_NAME_PRESIGN)),
			null,
			cal,
			extraParams
		);

		LOGGER.info("Se invocan las funciones internas de postfirma PAdES"); //$NON-NLS-1$
		final byte[] postsign = PAdESTriPhaseSigner.postSign(
			signatureAlgorithm,
			docBytes,
			cert,
			Base64.decode(signConfig.getProperty(PROPERTY_NAME_PKCS1_SIGN)),
			signResult,
			AOPDFSigner.getSignEnhancer(), // SignEnhancer
			AOPDFSigner.getSignEnhancerConfig(),  // EnhancerConfig (si le llega null usa los ExtraParams)
			true
		);

		LOGGER.info("Postfirma PAdES - Firma - FIN"); //$NON-NLS-1$

		return postsign;
	}

	/** Comprueba que la configuraci&oacute;n proporcionada para realizar la <i>postfirma</i> incluya
	 * todos los datos obligatorios para la operaci&oacute;n.
	 * @param sessionConfig Configuraci&oacute;n de firma.
	 * @throws AOException Cuando falta alg&uacute;n par&aacute;metro obligatorio. */
	private static void checkSession(final TriSign sessionConfig) throws AOException {

		final String[] params = new String[] {
			PROPERTY_NAME_PRESIGN,
			PROPERTY_NAME_PKCS1_SIGN,
			PROPERTY_NAME_PDF_UNIQUE_ID,
			PROPERTY_NAME_SIGN_TIME
		};
		for (final String param : params) {
			if (sessionConfig.getProperty(param) == null) {
				throw new AOException(
					"No se ha proporcionado un parametro obligatorio para la postfirma PAdES: " + param //$NON-NLS-1$
				);
			}
		}
	}

	@Override
	public TriphaseData preProcessPreCoSign(final byte[] data,
			                          final String signatureAlgorithm,
			                          final X509Certificate[] cert,
			                          final Properties extraParams,
				                      final boolean checkSignatures) throws IOException,
			                                                               AOException {
		return preProcessPreSign(data, signatureAlgorithm, cert, extraParams, checkSignatures);
	}

	@Override
	public byte[] preProcessPostCoSign(final byte[] data,
			                           final String signatureAlgorithm,
			                           final X509Certificate[] cert,
			                           final Properties extraParams,
			                           final byte[] session) throws NoSuchAlgorithmException,
			                                                        AOException,
			                                                        IOException {
		return preProcessPostSign(data, signatureAlgorithm, cert, extraParams, TriphaseData.parser(session));
	}

	@Override
	public byte[] preProcessPostCoSign(final byte[] data,
			                           final String signatureAlgorithm,
			                           final X509Certificate[] cert,
			                           final Properties extraParams,
			                           final TriphaseData triphaseData) throws NoSuchAlgorithmException,
			                                                        AOException,
			                                                        IOException {
		return preProcessPostSign(data, signatureAlgorithm, cert, extraParams, triphaseData);
	}

	@Override
	public TriphaseData preProcessPreCounterSign(final byte[] sign,
			                               final String signatureAlgorithm,
			                               final X509Certificate[] cert,
			                               final Properties extraParams,
			                               final CounterSignTarget targets,
					                       final boolean checkSignatures) throws IOException, AOException {
		throw new UnsupportedOperationException("La operacion de contrafirma no esta soportada en PAdES."); //$NON-NLS-1$
	}

	@Override
	public byte[] preProcessPostCounterSign(final byte[] sign,
			                                final String signatureAlgorithm,
			                                final X509Certificate[] cert,
			                                final Properties extraParams,
			                                final TriphaseData triphaseData,
			                                final CounterSignTarget targets) throws NoSuchAlgorithmException, AOException, IOException {
		throw new UnsupportedOperationException("La operacion de contrafirma no esta soportada en PAdES."); //$NON-NLS-1$
	}

	@Override
	public byte[] preProcessPostCounterSign(final byte[] sign,
			                                final String signatureAlgorithm,
			                                final X509Certificate[] cert,
			                                final Properties extraParams,
			                                final byte[] session,
			                                final CounterSignTarget targets) throws NoSuchAlgorithmException, AOException, IOException {
		throw new UnsupportedOperationException("La operacion de contrafirma no esta soportada en PAdES."); //$NON-NLS-1$
	}

	public void setMaxPagesToCheckPSA(final String maxPagesToCheckPSA) {
		this.maxPagesToCheckPSA = maxPagesToCheckPSA;
	}

	public void setPagesToCheckShadowAttack(final String pagesToCheckShadowAttack) {
		this.pagesToCheckShadowAttack = pagesToCheckShadowAttack;
	}

	public void setAllowPdfShadowAttack(final String allowPdfShadowAttack) {
		this.allowPdfShadowAttack = allowPdfShadowAttack;
	}

}
