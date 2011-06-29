/*
 * Este fichero forma parte del Cliente @firma. 
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo licencia GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este 
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.standalone.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;

import es.gob.afirma.misc.AOUtil;
import es.gob.afirma.signature.SignValidity;
import es.gob.afirma.signature.SignValidity.SIGN_DETAIL_TYPE;
import es.gob.afirma.signature.ValidateBinarySignature;
import es.gob.afirma.signature.ValidateXMLSignature;
import es.gob.afirma.signers.AOCAdESSigner;
import es.gob.afirma.signers.AOCMSSigner;
import es.gob.afirma.standalone.DataAnalizerUtil;
import es.gob.afirma.standalone.Messages;
import es.gob.afirma.standalone.SimpleAfirma;

/** Panel para la espera y detecci&oacute;n autom&aacute;tica de insercci&oacute;n de DNIe.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s
 * @author Carlos Gamuci
 */
public final class VisorPanel extends JPanel {

    /** Version ID */
    private static final long serialVersionUID = 8309157734617505338L;
    
    private final JButton openSign = new JButton();
    
    private ActionListener actionListener = null;
    
    
    /** Construye un panel de espera a insercci&oacute;n de DNIe.
     * @param al ActionListener para el control de los botones
     * @param safirma SimpleAfirma para establecer el <code>Locale</code> seleccionado en el men&uacute; desplegable */
    public VisorPanel(final File signFile, final byte[] sign, final ActionListener al) {
        super(true);
        this.actionListener = al;
        createUI(signFile, sign);
    }
    
    private void createUI(final File signFile, final byte[] sign) {
        this.setBackground(SimpleAfirma.WINDOW_COLOR);
        this.setLayout(new GridBagLayout());
        this.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        openSign(signFile, sign);
    }
    
    private void openSign(final File signFile, byte[] sign) {

        if (signFile == null && sign == null) {
            Logger.getLogger("es.gob.afirma").warning("Se ha intentado abrir una firma nula");
            return;
        }
                
        if (sign == null) {
            if (signFile != null) {
                try {
                    FileInputStream fis = new FileInputStream(signFile);
                    sign = AOUtil.getDataFromInputStream(fis);
                    try { fis.close(); } catch (Exception e) { }
                } catch (Exception e) {
                    Logger.getLogger("No se ha podido cargar el fichero de firma: " + e);
                }
            }
        }
        
        SignValidity validity = new SignValidity(SIGN_DETAIL_TYPE.UNKNOWN, null);
        if (sign != null) {
            try {
                validity = validateSign(sign);
            } catch (Exception e) {
                validity = new SignValidity(SIGN_DETAIL_TYPE.KO, null);
            }
        }
        
        final JPanel resultPanel = new SignResultPanel(validity);
        final JPanel dataPanel = new SignDataPanel(signFile, sign, null, null);

        final JPanel bottonPanel = new JPanel(true);
        bottonPanel.setLayout(new BoxLayout(bottonPanel, BoxLayout.Y_AXIS));
        bottonPanel.setBackground(SimpleAfirma.WINDOW_COLOR);
        this.openSign.setText(Messages.getString("VisorPanel.1")); //$NON-NLS-1$
        this.openSign.setMnemonic('a');
        this.openSign.setAlignmentX(Component.CENTER_ALIGNMENT);
        bottonPanel.add(this.openSign);
        this.openSign.addActionListener(this.actionListener);

        setLayout(new GridBagLayout());

        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.insets = new Insets(11, 11, 11, 11);
        add(resultPanel, c);
        c.weighty = 1.0;
        c.gridy = 1;
        c.insets = new Insets(0, 11, 11, 11);
        add(dataPanel, c);
        c.weighty = 0.0;
        c.gridy = 2;
        c.insets = new Insets(0, 11, 11, 11);
        add(bottonPanel, c);
        
        repaint();
    }
    
    /**
     * Comprueba la validez de la firma.
     * @param sign Firma que se desea comprobar.
     * @return {@code true} si la firma es v&acute;lida, {@code false} en caso contrario.
     * @throws Exception Cuando los datos introducidos no se corresponden con una firma.
     */
    private SignValidity validateSign(byte[] sign) throws Exception {
        
        if (DataAnalizerUtil.isPDF(sign)) {
            return new SignValidity(SIGN_DETAIL_TYPE.OK, null);
        } else if (DataAnalizerUtil.isXML(sign)) {
            return ValidateXMLSignature.validate(sign);
        } else if(new AOCMSSigner().isSign(sign) || new AOCAdESSigner().isSign(sign)) {
            return ValidateBinarySignature.validate(sign);
        }
        return new SignValidity(SIGN_DETAIL_TYPE.KO, null);
    }
    
//    public static void main(String[] args) {
//        
//        File signFile = new File("C:/Users/A122466/Desktop/Escritorio/Firma.csig");
//        
//        JPanel currentPanel = new VisorPanel(signFile, null, null);
//        Container container = new MainScreen(null, currentPanel);
//        Window window = (JFrame) container;
//        
//    }
}
