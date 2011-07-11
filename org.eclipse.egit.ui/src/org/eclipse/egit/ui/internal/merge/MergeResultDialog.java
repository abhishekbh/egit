/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.merge;

import java.io.IOException;

import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.commit.CommitEditor;
import org.eclipse.egit.ui.internal.commit.RepositoryCommit;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog for displaying a MergeResult
 *
 */
public class MergeResultDialog extends Dialog {

	private static final String SPACE = " "; //$NON-NLS-1$

	private static final String EMPTY = ""; //$NON-NLS-1$

	private final MergeResult mergeResult;

	private final Repository repository;

	private ObjectReader objectReader;

	/**
	 * @param parentShell
	 * @param repository
	 * @param mergeResult
	 */
	public MergeResultDialog(Shell parentShell, Repository repository,
			MergeResult mergeResult) {
		super(parentShell);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		this.repository = repository;
		this.mergeResult = mergeResult;
		objectReader = repository.newObjectReader();
	}

	@Override
	protected void createButtonsForButtonBar(final Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL,
				true);
	}

	@Override
	public Control createDialogArea(final Composite parent) {
		final Composite composite = (Composite) super.createDialogArea(parent);
		GridLayout gridLayout = new GridLayout();
		gridLayout.numColumns = 2;
		composite.setLayout(gridLayout);
		// result
		Label resultLabel = new Label(composite, SWT.NONE);
		resultLabel.setText(UIText.MergeResultDialog_result);
		resultLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false,
				false));
		Text resultText = new Text(composite, SWT.READ_ONLY);
		resultText.setText(mergeResult.getMergeStatus().toString());
		resultText.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		// new head
		Label newHeadLabel = new Label(composite, SWT.NONE);
		newHeadLabel.setText(UIText.MergeResultDialog_newHead);
		newHeadLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false,
				false));
		Text newHeadText = new Text(composite, SWT.READ_ONLY);
		ObjectId newHead = mergeResult.getNewHead();
		if (newHead != null)
			newHeadText.setText(getCommitMessage(newHead) + SPACE
					+ abbreviate(mergeResult.getNewHead(), true));
		newHeadText
				.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
		// Merge Input
		Label mergeInputLabel = new Label(composite, SWT.NONE);
		mergeInputLabel.setText(UIText.MergeResultDialog_mergeInput);
		GridDataFactory.fillDefaults().align(SWT.LEAD, SWT.CENTER).span(2, 1)
				.applyTo(mergeInputLabel);
		TableViewer viewer = new TableViewer(composite);
		viewer.setContentProvider(new IStructuredContentProvider() {

			public void dispose() {
				// empty
			}

			public void inputChanged(Viewer theViewer, Object oldInput,
					Object newInput) {
				// empty
			}

			public Object[] getElements(Object inputElement) {
				return mergeResult.getMergedCommits();
			}
		});
		TableViewerColumn idColumn = new TableViewerColumn(viewer, SWT.LEFT);
		idColumn.getColumn().setText(UIText.MergeResultDialog_id);
		idColumn.getColumn().setWidth(100);
		TableViewerColumn textColumn = new TableViewerColumn(viewer, SWT.LEFT);
		textColumn.getColumn().setText(UIText.MergeResultDialog_description);
		textColumn.getColumn().setWidth(300);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		viewer.setLabelProvider(new ITableLabelProvider() {

			public void removeListener(ILabelProviderListener listener) {
				// empty
			}

			public boolean isLabelProperty(Object element, String property) {
				return false;
			}

			public void dispose() {
				// empty
			}

			public void addListener(ILabelProviderListener listener) {
				// empty
			}

			public String getColumnText(Object element, int columnIndex) {
				ObjectId commitId = (ObjectId) element;
				if (columnIndex == 0)
					return abbreviate(commitId, false);
				else if (columnIndex == 1)
					return getCommitMessage(commitId);
				return EMPTY;
			}

			public Image getColumnImage(Object element, int columnIndex) {
				return null;
			}
		});
		applyDialogFont(composite);
		GridDataFactory.fillDefaults().grab(true, true)
				.align(SWT.FILL, SWT.FILL).span(2, 1)
				.applyTo(viewer.getControl());
		viewer.setInput(mergeResult);
		viewer.addOpenListener(new IOpenListener() {

			public void open(OpenEvent event) {
				ISelection selection = event.getSelection();
				if (selection instanceof IStructuredSelection) {
					for (Object element : ((IStructuredSelection) selection)
							.toArray())
						if (element instanceof ObjectId)
							openCommit((ObjectId) element);
				}
			}
		});
		return composite;
	}

	private void openCommit(ObjectId id) {
		try {
			RevCommit commit = new RevWalk(repository).parseCommit(id);
			CommitEditor.openQuiet(new RepositoryCommit(repository, commit));
		} catch (IOException e) {
			Activator.logError(UIText.MergeResultDialog_couldNotFindCommit, e);
		}
	}

	private String getCommitMessage(ObjectId id) {
		RevCommit commit;
		try {
			commit = new RevWalk(repository).parseCommit(id);
		} catch (IOException e) {
			Activator.logError(UIText.MergeResultDialog_couldNotFindCommit, e);
			return UIText.MergeResultDialog_couldNotFindCommit;
		}
		return commit.getShortMessage();
	}

	private String abbreviate(ObjectId id, boolean addBrackets) {
		StringBuilder result = new StringBuilder(EMPTY);
		if (addBrackets)
			result.append("["); //$NON-NLS-1$
		try {
			result.append(objectReader.abbreviate(id).name());
		} catch (IOException e) {
			result.append(id.name());
		}
		if (addBrackets)
			result.append("]"); //$NON-NLS-1$
		return result.toString();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(UIText.MergeAction_MergeResultTitle);
		newShell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				if (objectReader != null)
					objectReader.release();
			}
		});
	}
}
