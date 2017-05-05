/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.actions;

import java.io.IOException;

import org.archicontribs.modelrepository.IModelRepositoryImages;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;

/**
 * Open Model Action
 * 
 * @author Jean-Baptiste Sarrodie
 * @author Phillip Beauvoir
 */
public class OpenModelAction extends AbstractModelAction {
	
	private IWorkbenchWindow fWindow;

    public OpenModelAction(IWorkbenchWindow window) {
        fWindow = window;
        setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_OPEN_16));
        setText("Open");
        setToolTipText("Open Model");
    }

    @Override
    public void run() {
        if(IEditorModelManager.INSTANCE.isModelLoaded(GraficoUtils.TEST_LOCAL_FILE)) {
            MessageDialog.openInformation(fWindow.getShell(),
                    "Open",
                    "Model is already open.");
        }
        else {
            try {
                GraficoUtils.loadModel(getGitRepository(), fWindow.getShell());
            }
            catch(IOException ex) {
                ex.printStackTrace();
            }
        }

    }
}