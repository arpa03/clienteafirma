/*
 * Este fichero forma parte del Cliente @firma. 
 * El Cliente @firma es un aplicativo de libre distribucion cuyo codigo fuente puede ser consultado
 * y descargado desde www.ctt.map.es.
 * Copyright 2009,2010,2011 Gobierno de Espana
 * Este fichero se distribuye bajo las licencias EUPL version 1.1 y GPL version 3 segun las
 * condiciones que figuran en el fichero 'licence' que se acompana. Si se distribuyera este 
 * fichero individualmente, deben incluirse aqui las condiciones expresadas alli.
 */

package es.gob.afirma.misc;

import java.awt.Frame;
import java.io.FileOutputStream;
import java.security.AccessController;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import es.gob.afirma.ui.AOUIManager;

/**
 * Invocar preferentemente de la siguiente manera:<br> 
 * <code>SwingUtilities.invokeLater(new AsinchronousSaveData(data, file, desc, exts, parent, true));</code>
 */
public final class AsinchronousSaveData implements Runnable {

	private byte[] dataToSave = null;
	private String savingTarget = null;
	private String[] extensions = null;
	private String description = "Firma digital";
	private Frame parent = null;
	private boolean showDialogIfError = true;
	
	
	/**
	 * Crea una clase para el guardado as&iacute;ncrono de datos en disco.
	 * @param data Datos a guardar
	 * @param fileName Nombre del fichero destino
	 * @param description Descripci&oacute;n del fichero destino
	 * @param extensions Posibles extensiones para el fichero destino
	 * @param parent Componente padre para la modalidad
	 * @param errorDialog <code>true</code> si queremos mostrar un di&aacute;logo gr&aacute;fico en caso
	 *                    de error, <code>false</code> en caso contrario
	 */
	public AsinchronousSaveData(final byte[] data, 
			                    final String fileName, 
			                    final String description, 
			                    final String[] extensions, 
			                    final Frame parent, 
			                    final boolean errorDialog) {
		setDataToSave(data);
		setSavingTarget(fileName);
		setDescription(description);
		setExtensions(extensions);
		setParentComponent(parent);
		setShowDialogIfError(errorDialog);
	}
	
	/**
	 * Indica si se debe mostrar un di&aacute;logo de advertencia en caso de fallo
	 * en la operaci&oacute;n
	 * @param show <code>true</code> para mostrar el di&aacute;logo, <code>false</code> en caso contrario
	 */
	public void setShowDialogIfError(boolean show) {
		showDialogIfError = show;
	}
	
	/**
	 * Establece la descripci&oacute;n del archivo a guardar.
	 * @param desc Descripci&oacute;n del archivo a guardar
	 */
	public void setDescription(final String desc) {
		if (desc != null && !"".equals(desc)) description = desc;
	}
	
	/**
	 * Establece las posibles extensiones sujeridas para guardar el fichero.
	 * @param exts Extensiones posibles para guardar el fichero (no incluir el punto)
	 */
	public void setExtensions(final String[] exts) {
		if (exts != null && exts.length > 0) extensions = exts;
	}
	
	/**
	 * Establece los datos que queremos guardar en disco.
	 * @param data Datos a guardar como fichero
	 */
	public void setDataToSave(byte[] data) {
		if (data != null && data.length > 0) dataToSave = data;
	}
	
	/**
	 * Establece el nombre de fichero en el que se guardar&aacute;n los datos. Si no se
	 * establece se pregunta al usuario por uno con un di&aacute;logo gr&aacute;fico
	 * @param target Nombre de fichero
	 */
	public void setSavingTarget(String target) {
		savingTarget = target;
	}
	
	/**
	 * Establece el componente padre para la modalidad.
	 * @param p Componente padre
	 */
	public void setParentComponent(Frame p) {
		parent = p;
	}
	
	public void run() {
		AccessController.doPrivileged(new java.security.PrivilegedAction<Void>() {
			public Void run() {
				if (dataToSave == null || dataToSave.length < 1) {
					Logger.getLogger("es.gob.afirma").warning(
						"No se han proporcionado datos a guardar, se abortara la operacion"
					);
					return null;
				}
				if (savingTarget == null || "".equals(savingTarget)) {
					try {
						savingTarget = AOUIManager.getSaveFileName(extensions, description, parent);
					}
					catch(Throwable e) {
						Logger.getLogger("es.gob.afirma").severe(
							"El nombre de fichero para guardar los datos no es valido: " + e
						);
						return null;
					}
					if (savingTarget == null) {
						Logger.getLogger("es.gob.afirma").severe(
							"No se establecio un nombre de fichero de salida"
						);
						return null;
					}
				}

				// Aqui ya tenemos un nombre de salida
				FileOutputStream fos = null;
				try {
					fos = new FileOutputStream(savingTarget);
					fos.write(dataToSave);
					fos.flush();
				} 
				catch (Throwable e) {
					Logger.getLogger("es.gob.afirma").severe(
						"No se pudieron almacenar los datos en disco: " + e
					);
					if (showDialogIfError) {
						JOptionPane.showMessageDialog(
								parent,
								"Ocurri\u00F3 un error al almacenar los datos en disco,\r\nlos datos no se han guardado.",
								"Error",
								JOptionPane.ERROR_MESSAGE
						);
					}
				}
				finally {
				    if(fos != null) {
				        try {
				            fos.close();
				        }
				        catch(Exception e) {
				            Logger.getLogger("es.gob.afirma").warning(
			            		"No se ha podido cerrar el fichero de salida, es posible que se no se pueda abrir hasta cerrar la aplicacion"
		            		);
				        }
				    }
				}
				return null;
			}
		});
	}

}
