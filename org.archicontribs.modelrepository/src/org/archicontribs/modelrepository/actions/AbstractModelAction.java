/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.actions;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.archicontribs.modelrepository.ModelRepositoryPlugin;
import org.archicontribs.modelrepository.authentication.SimpleCredentialsStorage;
import org.archicontribs.modelrepository.authentication.UsernamePassword;
import org.archicontribs.modelrepository.dialogs.CommitDialog;
import org.archicontribs.modelrepository.dialogs.UserNamePasswordDialog;
import org.archicontribs.modelrepository.grafico.GraficoModelExporter;
import org.archicontribs.modelrepository.grafico.GraficoModelImporter;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IArchiRepository;
import org.archicontribs.modelrepository.grafico.RepositoryListenerManager;
import org.archicontribs.modelrepository.preferences.IPreferenceConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateModel;

/**
 * Abstract ModelAction
 * 
 * @author Phillip Beauvoir
 */
public abstract class AbstractModelAction extends Action implements IGraficoModelAction {
	
	private IArchiRepository fRepository;
	
	protected IWorkbenchWindow fWindow;
	
	protected AbstractModelAction(IWorkbenchWindow window) {
	    fWindow = window;
	}
	
	@Override
	public void setRepository(IArchiRepository repository) {
	    fRepository = repository;
	    setEnabled(shouldBeEnabled());
	}
	
	@Override
	public IArchiRepository getRepository() {
	    return fRepository;
	}
	 
	/**
	 * @return true if this action should be enabled
	 */
	protected boolean shouldBeEnabled() {
	    return getRepository() != null;
	}
	
    /**
     * Display an errror dialog
     * @param title
     * @param ex
     */
    protected void displayErrorDialog(String title, Throwable ex) {
        ex.printStackTrace();
        
        MessageDialog.openError(fWindow.getShell(),
                title,
                Messages.AbstractModelAction_0 +
                    " " + //$NON-NLS-1$
                    ex.getMessage());
    }

    /**
     * Offer to save the model
     * @param model
     */
    protected boolean offerToSaveModel(IArchimateModel model) {
        boolean response = MessageDialog.openConfirm(fWindow.getShell(),
                Messages.AbstractModelAction_1,
                Messages.AbstractModelAction_2);

        if(response) {
            try {
                IEditorModelManager.INSTANCE.saveModel(model);
            }
            catch(IOException ex) {
                displayErrorDialog(Messages.AbstractModelAction_1, ex);
            }
        }
        
        return response;
    }
    
    /**
     * Load the model from the Grafico XML files
     * @return the model or null if there are no Grafico files
     * @throws IOException
     */
    protected IArchimateModel loadModelFromGraficoFiles() throws IOException {
        GraficoModelImporter importer = new GraficoModelImporter();
        IArchimateModel graficoModel = importer.importLocalGitRepositoryAsModel(getRepository().getLocalRepositoryFolder());
        
        if(graficoModel != null) {
            File tmpFile = fRepository.getTempModelFile();
            graficoModel.setFile(tmpFile);
            
            // Errors
            if(importer.getResolveStatus() != null) {
                ErrorDialog.openError(fWindow.getShell(),
                        Messages.AbstractModelAction_3,
                        Messages.AbstractModelAction_4,
                        importer.getResolveStatus());

            }
            
            // Close the real model if it is already open
            IArchimateModel model = fRepository.locateModel();
            if(model != null) {
                IEditorModelManager.INSTANCE.closeModel(model);
            }
            
            // Open it with the new grafico model, this will do the necessary checks and add a command stack and an archive manager
            IEditorModelManager.INSTANCE.openModel(graficoModel);
            
            // And Save it to the temp file
            IEditorModelManager.INSTANCE.saveModel(graficoModel);
        }
        
        return graficoModel;
    }
    
    /**
     * Export the model to Grafico files
     */
    protected void exportModelToGraficoFiles() {
        // Open the model
        IArchimateModel model = IEditorModelManager.INSTANCE.openModel(fRepository.getTempModelFile());
        
        if(model == null) {
            MessageDialog.openError(fWindow.getShell(),
                    Messages.AbstractModelAction_7,
                    Messages.AbstractModelAction_8);
            return;
        }
        
        try {
            GraficoModelExporter exporter = new GraficoModelExporter();
            exporter.exportModelToLocalGitRepository(model, getRepository().getLocalRepositoryFolder());
        }
        catch(IOException ex) {
            displayErrorDialog(Messages.AbstractModelAction_5, ex);
        }
    }
    
    /**
     * Offer to Commit changes
     * @return true if successful, false otherwise
     */
    protected boolean offerToCommitChanges() {
        CommitDialog commitDialog = new CommitDialog(fWindow.getShell());
        int response = commitDialog.open();
        
        if(response == Window.OK) {
            String userName = commitDialog.getUserName();
            String userEmail = commitDialog.getUserEmail();
            String commitMessage = commitDialog.getCommitMessage();
            PersonIdent personIdent = new PersonIdent(userName, userEmail);
            
            // Store Prefs
            ModelRepositoryPlugin.INSTANCE.getPreferenceStore().setValue(IPreferenceConstants.PREFS_COMMIT_USER_NAME, userName);
            ModelRepositoryPlugin.INSTANCE.getPreferenceStore().setValue(IPreferenceConstants.PREFS_COMMIT_USER_EMAIL, userEmail);

            try {
                GraficoUtils.commitChanges(getRepository().getLocalRepositoryFolder(), personIdent, commitMessage);
            }
            catch(IOException | GitAPIException ex) {
                displayErrorDialog(Messages.AbstractModelAction_6, ex);
                return false;
            }
            
            return true;
        }
        
        return false;
    }
    
    /**
     * Get user name and password from credentials file if prefs set or from dialog
     * @param storageFileName
     * @param shell
     * @return
     */
    protected UsernamePassword getUserNameAndPasswordFromCredentialsFileOrDialog(String storageFileName, Shell shell) {
        boolean doStoreInCredentialsFile = ModelRepositoryPlugin.INSTANCE.getPreferenceStore().getBoolean(IPreferenceConstants.PREFS_STORE_REPO_CREDENTIALS);
        
        SimpleCredentialsStorage sc = new SimpleCredentialsStorage(getRepository().getLocalGitFolder(), storageFileName);

        // Is it stored?
        if(doStoreInCredentialsFile && sc.hasCredentialsFile()) {
            try {
                return new UsernamePassword(sc.getUsername(), sc.getPassword());
            }
            catch(IOException ex) {
                displayErrorDialog(Messages.AbstractModelAction_9, ex);
            }
        }
        
        // Else ask the user
        UserNamePasswordDialog dialog = new UserNamePasswordDialog(shell);
        if(dialog.open() != Window.OK) {
            return null;
        }

        UsernamePassword up = new UsernamePassword(dialog.getUsername(), dialog.getPassword());

        // Store credentials if option is set
        if(doStoreInCredentialsFile) {
            try {
                sc.store(up.getUsername(), up.getPassword());
            }
            catch(NoSuchAlgorithmException | IOException ex) {
                displayErrorDialog(Messages.AbstractModelAction_10, ex);
            }
        }

        return up;
    }
    
    /**
     * Notify that the repo changed
     */
    protected void notifyChangeListeners(String eventName) {
        RepositoryListenerManager.INSTANCE.fireRepositoryChangedEvent(eventName, getRepository());
    }
    
    @Override
    public void dispose() {
    }
}
