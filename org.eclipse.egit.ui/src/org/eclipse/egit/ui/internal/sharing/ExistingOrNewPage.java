/*******************************************************************************
 * Copyright (C) 2009, 2011 Robin Rosenberg
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2011, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.sharing;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.project.RepositoryFinder;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.repository.NewRepositoryWizard;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;

/**
 * Wizard page for connecting projects to Git repositories.
 */
class ExistingOrNewPage extends WizardPage {

	private final SharingWizard myWizard;

	private Button button;

	private Tree tree;

	private CheckboxTreeViewer viewer;

	private Text repositoryToCreate;

	private IPath minumumPath;

	private Text dotGitSegment;

	private Composite externalComposite;

	private Composite parentRepoComposite;

	private Text workDir;

	private Text relPath;

	private Button browseRepository;

	private Repository selectedRepository;

	private CheckboxTableViewer projectMoveViewer;

	private final MoveProjectsLabelProvider moveProjectsLabelProvider = new MoveProjectsLabelProvider();

	private boolean internalMode = false;

	ExistingOrNewPage(SharingWizard w) {
		super(ExistingOrNewPage.class.getName());
		setTitle(UIText.ExistingOrNewPage_title);
		setImageDescriptor(UIIcons.WIZBAN_CONNECT_REPO);
		this.myWizard = w;
	}

	public void createControl(Composite parent) {
		final RepositoryUtil util = Activator.getDefault().getRepositoryUtil();
		Composite main = new Composite(parent, SWT.NONE);
		// use zero spacing to save some real estate here
		GridLayoutFactory.fillDefaults().spacing(0, 0).applyTo(main);

		final Button internalModeButton = new Button(main, SWT.CHECK);
		internalModeButton
				.setText(UIText.ExistingOrNewPage_InternalModeCheckbox);
		internalModeButton
				.setToolTipText(UIText.ExistingOrNewPage_CreationInWorkspaceWarningTooltip);
		internalModeButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				internalMode = internalModeButton.getSelection();
				updateControls();
			}
		});

		externalComposite = new Composite(main, SWT.NONE);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(externalComposite);
		externalComposite.setLayout(new GridLayout(3, false));

		new Label(externalComposite, SWT.NONE)
				.setText(UIText.ExistingOrNewPage_ExistingRepositoryLabel);
		final Combo existingRepoCombo = new Combo(externalComposite,
				SWT.READ_ONLY);
		GridDataFactory.fillDefaults().grab(true, false)
				.applyTo(existingRepoCombo);
		final ComboViewer v = new ComboViewer(existingRepoCombo);
		v.setContentProvider(new RepoComboContentProvider());
		v.setLabelProvider(new RepoComboLabelProvider());
		v.setInput(new Object());
		// the default ViewerSorter seems to do the right thing
		// i.e. sort as String
		v.setSorter(new ViewerSorter());

		existingRepoCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectedRepository = null;
				IStructuredSelection sel = (IStructuredSelection) v
						.getSelection();
				setRepository((Repository) sel.getFirstElement());
				updateControls();
			}
		});

		Button newRepo = new Button(externalComposite, SWT.PUSH);
		newRepo.setText(UIText.ExistingOrNewPage_CreateRepositoryButton);
		newRepo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				NewRepositoryWizard wiz = new NewRepositoryWizard(true);
				if (new WizardDialog(getShell(), wiz).open() == Window.OK) {
					v.refresh();
					selectedRepository = wiz.getCreatedRepository();
					v.setSelection(new StructuredSelection(selectedRepository));
					updateControls();
				}
			}
		});

		new Label(externalComposite, SWT.NONE)
				.setText(UIText.ExistingOrNewPage_WorkingDirectoryLabel);
		workDir = new Text(externalComposite, SWT.BORDER | SWT.READ_ONLY);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(workDir);
		GridDataFactory.fillDefaults().applyTo(workDir);
		// leave the space between the "Create" and "Browse" buttons empty (i.e.
		// do not fill to the right border
		new Label(externalComposite, SWT.NONE);

		new Label(externalComposite, SWT.NONE)
				.setText(UIText.ExistingOrNewPage_RelativePathLabel);
		relPath = new Text(externalComposite, SWT.BORDER);
		relPath.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateControls();
			}
		});

		GridDataFactory.fillDefaults().grab(true, false).applyTo(relPath);
		browseRepository = new Button(externalComposite, SWT.PUSH);
		browseRepository.setEnabled(false);
		browseRepository
				.setText(UIText.ExistingOrNewPage_BrowseRepositoryButton);
		browseRepository.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dlg = new DirectoryDialog(getShell());
				dlg.setFilterPath(selectedRepository.getWorkTree().getPath());
				setRelativePath(dlg.open());
				updateControls();
			}
		});

		Table projectMoveTable = new Table(externalComposite, SWT.MULTI
				| SWT.FULL_SELECTION | SWT.CHECK);
		projectMoveViewer = new CheckboxTableViewer(projectMoveTable);
		GridDataFactory.fillDefaults().span(3, 1).grab(true, true)
				.applyTo(projectMoveTable);

		TableColumn tc;
		tc = new TableColumn(projectMoveTable, SWT.NONE);
		tc.setText(UIText.ExistingOrNewPage_ProjectNameColumnHeader);
		tc.setWidth(150);

		tc = new TableColumn(projectMoveTable, SWT.NONE);
		tc.setText(UIText.ExistingOrNewPage_CurrentLocationColumnHeader);
		tc.setWidth(250);

		tc = new TableColumn(projectMoveTable, SWT.NONE);
		tc.setText(UIText.ExistingOrNewPage_NewLocationTargetHeader);
		tc.setWidth(350);

		projectMoveTable.setHeaderVisible(true);
		projectMoveViewer
				.setContentProvider(ArrayContentProvider.getInstance());
		projectMoveViewer.setLabelProvider(moveProjectsLabelProvider);
		projectMoveViewer.setInput(myWizard.projects);
		projectMoveViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				updateControls();
			}
		});
		TableItem[] children = projectMoveViewer.getTable().getItems();
		for (int i = 0; i < children.length; i++) {
			TableItem item = children[i];
			IProject data = (IProject) item.getData();
			RepositoryFinder repositoryFinder = new RepositoryFinder(data);
			try {
				Collection<RepositoryMapping> find = repositoryFinder
						.find(new NullProgressMonitor());
				if (find.size() != 1)
					item.setChecked(true);
			} catch (CoreException e1) {
				item.setText(2, e1.getMessage());
			}
		}

		parentRepoComposite = new Composite(main, SWT.NONE);
		parentRepoComposite.setLayout(new GridLayout(3, false));
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(parentRepoComposite);

		tree = new Tree(parentRepoComposite, SWT.BORDER | SWT.MULTI
				| SWT.FULL_SELECTION | SWT.CHECK);
		viewer = new CheckboxTreeViewer(tree);
		tree.setHeaderVisible(true);
		tree.setLayout(new GridLayout());
		tree.setLayoutData(GridDataFactory.fillDefaults().grab(true, true)
				.span(3, 1).create());
		viewer.addCheckStateListener(new ICheckStateListener() {

			public void checkStateChanged(CheckStateChangedEvent event) {
				if (event.getChecked()) {
					ProjectAndRepo checkable = (ProjectAndRepo) event
							.getElement();
					for (TreeItem ti : tree.getItems()) {
						if (ti.getItemCount() > 0
								|| ((ProjectAndRepo) ti.getData()).getRepo()
										.equals("")) //$NON-NLS-1$
							ti.setChecked(false);
						for (TreeItem subTi : ti.getItems()) {
							IProject project = ((ProjectAndRepo) subTi
									.getData()).getProject();
							if (checkable.getProject() != null
									&& !subTi.getData().equals(checkable)
									&& checkable.getProject().equals(project))
								subTi.setChecked(false);
						}
					}
				}
			}
		});
		TreeColumn c1 = new TreeColumn(tree, SWT.NONE);
		c1.setText(UIText.ExistingOrNewPage_HeaderProject);
		c1.setWidth(100);
		TreeColumn c2 = new TreeColumn(tree, SWT.NONE);
		c2.setText(UIText.ExistingOrNewPage_HeaderPath);
		c2.setWidth(400);
		TreeColumn c3 = new TreeColumn(tree, SWT.NONE);
		c3.setText(UIText.ExistingOrNewPage_HeaderRepository);
		c3.setWidth(200);
		for (IProject project : myWizard.projects) {
			RepositoryFinder repositoryFinder = new RepositoryFinder(project);
			try {
				Collection<RepositoryMapping> mappings;
				mappings = repositoryFinder.find(new NullProgressMonitor());
				Iterator<RepositoryMapping> mi = mappings.iterator();
				RepositoryMapping m = mi.hasNext() ? mi.next() : null;
				if (m == null) {
					// no mapping found, enable repository creation
					TreeItem treeItem = new TreeItem(tree, SWT.NONE);
					treeItem.setText(0, project.getName());
					treeItem.setText(1, project.getLocation().toOSString());
					treeItem.setText(2, ""); //$NON-NLS-1$
					treeItem.setData(new ProjectAndRepo(project, "")); //$NON-NLS-1$
				} else if (!mi.hasNext()) {
					// exactly one mapping found
					TreeItem treeItem = new TreeItem(tree, SWT.NONE);
					treeItem.setText(0, project.getName());
					treeItem.setText(1, project.getLocation().toOSString());
					fillTreeItemWithGitDirectory(m, treeItem, false);
					treeItem.setData(new ProjectAndRepo(project, treeItem
							.getText(2)));
					treeItem.setChecked(true);
				}

				else {
					TreeItem treeItem = new TreeItem(tree, SWT.NONE);
					treeItem.setText(0, project.getName());
					treeItem.setText(1, project.getLocation().toOSString());
					treeItem.setData(new ProjectAndRepo(null, null));

					TreeItem treeItem2 = new TreeItem(treeItem, SWT.NONE);
					treeItem2.setText(0, project.getName());
					fillTreeItemWithGitDirectory(m, treeItem2, true);
					treeItem2.setData(new ProjectAndRepo(project, treeItem2
							.getText(2)));
					while (mi.hasNext()) { // fill in additional mappings
						m = mi.next();
						treeItem2 = new TreeItem(treeItem, SWT.NONE);
						treeItem2.setText(0, project.getName());
						fillTreeItemWithGitDirectory(m, treeItem2, true);
						treeItem2.setData(new ProjectAndRepo(m.getContainer()
								.getProject(), treeItem2.getText(2)));
					}
					treeItem.setExpanded(true);
				}
			} catch (CoreException e) {
				TreeItem treeItem2 = new TreeItem(tree, SWT.BOLD | SWT.ITALIC);
				treeItem2.setText(e.getMessage());
			}
		}

		button = new Button(parentRepoComposite, SWT.PUSH);
		button.setLayoutData(GridDataFactory.fillDefaults().create());
		button.setText(UIText.ExistingOrNewPage_CreateButton);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				File gitDir = new File(repositoryToCreate.getText(),
						Constants.DOT_GIT);
				try {
					Repository repository = new FileRepository(gitDir);
					repository.create();
					for (IProject project : getProjects(false).keySet()) {
						// If we don't refresh the project directories right
						// now we won't later know that a .git directory
						// exists within it and we won't mark the .git
						// directory as a team-private member. Failure
						// to do so might allow someone to delete
						// the .git directory without us stopping them.
						// (Half lie, we should optimize so we do not
						// refresh when the .git is not within the project)
						//
						if (!gitDir.toString().contains("..")) //$NON-NLS-1$
							project.refreshLocal(IResource.DEPTH_ONE,
									new NullProgressMonitor());
					}
					util.addConfiguredRepository(gitDir);
				} catch (IOException e1) {
					String msg = NLS
							.bind(UIText.ExistingOrNewPage_ErrorFailedToCreateRepository,
									gitDir.toString());
					org.eclipse.egit.ui.Activator.handleError(msg, e1, true);
				} catch (CoreException e2) {
					String msg = NLS
							.bind(UIText.ExistingOrNewPage_ErrorFailedToRefreshRepository,
									gitDir);
					org.eclipse.egit.ui.Activator.handleError(msg, e2, true);
				}
				for (TreeItem ti : tree.getSelection()) {
					ti.setText(2, gitDir.toString());
					((ProjectAndRepo) ti.getData()).repo = gitDir.toString();
					ti.setChecked(true);
				}
				updateControls();
			}
		});
		repositoryToCreate = new Text(parentRepoComposite, SWT.SINGLE
				| SWT.BORDER);
		repositoryToCreate.setLayoutData(GridDataFactory.fillDefaults()
				.grab(true, false).span(1, 1).create());
		repositoryToCreate.addListener(SWT.Modify, new Listener() {
			public void handleEvent(Event e) {
				if (repositoryToCreate.getText().equals("")) { //$NON-NLS-1$
					button.setEnabled(false);
					return;
				}
				IPath fromOSString = Path.fromOSString(repositoryToCreate
						.getText());
				button.setEnabled(minumumPath
						.matchingFirstSegments(fromOSString) == fromOSString
						.segmentCount());
			}
		});
		dotGitSegment = new Text(parentRepoComposite, SWT.NONE);
		dotGitSegment.setEnabled(false);
		dotGitSegment.setEditable(false);
		dotGitSegment.setText(File.separatorChar + Constants.DOT_GIT);
		dotGitSegment.setLayoutData(GridDataFactory.fillDefaults()
				.align(SWT.LEFT, SWT.CENTER).create());

		tree.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				tree.select((TreeItem) e.item);
				updateControls();
			}
		});

		Dialog.applyDialogFont(main);
		setControl(main);
	}

	protected void setRelativePath(String directory) {
		IPath folderPath = new Path(directory).setDevice(null);
		IPath workdirPath = new Path(this.selectedRepository.getWorkTree()
				.getPath()).setDevice(null);
		if (!workdirPath.isPrefixOf(folderPath)) {
			MessageDialog.openError(getShell(),
					UIText.ExistingOrNewPage_WrongPathErrorDialogTitle,
					UIText.ExistingOrNewPage_WrongPathErrorDialogMessage);
			return;
		}
		relPath.setText(folderPath.removeFirstSegments(
				workdirPath.segmentCount()).toString());
	}

	protected void setRepository(Repository repository) {
		if (repository == this.selectedRepository)
			return;
		this.selectedRepository = repository;
		relPath.setText(""); //$NON-NLS-1$
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible)
			updateControls();
	}

	private void fillTreeItemWithGitDirectory(RepositoryMapping m,
			TreeItem treeItem, boolean isAlternative) {
		if (m.getGitDir() == null)
			treeItem.setText(2,
					UIText.ExistingOrNewPage_SymbolicValueEmptyMapping);
		else {
			IPath container = m.getContainerPath();
			if (!container.isEmpty())
				container = Path.fromOSString("."); //$NON-NLS-1$
			IPath relativePath = container.append(m.getGitDir());
			if (isAlternative) {
				IPath withoutLastSegment = relativePath.removeLastSegments(1);
				IPath path;
				if (withoutLastSegment.isEmpty())
					path = Path.fromPortableString("."); //$NON-NLS-1$
				else
					path = withoutLastSegment;
				treeItem.setText(0, path.toString());
			}
			treeItem.setText(2, relativePath.toOSString());
			try {
				IProject project = m.getContainer().getProject();
				FileRepository repo = new FileRepository(m
						.getGitDirAbsolutePath().toFile());
				File workTree = repo.getWorkTree();
				IPath workTreePath = Path.fromOSString(workTree
						.getAbsolutePath());
				if (workTreePath.isPrefixOf(project.getLocation())) {
					IPath makeRelativeTo = project.getLocation()
							.makeRelativeTo(workTreePath);
					String repoRelativePath = makeRelativeTo
							.append("/.project").toPortableString(); //$NON-NLS-1$
					ObjectId headCommitId = repo.resolve(Constants.HEAD);
					if (headCommitId != null) {
						// Not an empty repo
						RevWalk revWalk = new RevWalk(repo);
						RevCommit headCommit = revWalk
								.parseCommit(headCommitId);
						RevTree headTree = headCommit.getTree();
						TreeWalk projectInRepo = TreeWalk.forPath(repo,
								repoRelativePath, headTree);
						if (projectInRepo != null) {
							// the .project file is tracked by this repo
							treeItem.setChecked(true);
						}
						revWalk.dispose();
					}
				}
				repo.close();
			} catch (IOException e1) {
				Activator
						.logError(
								UIText.ExistingOrNewPage_FailedToDetectRepositoryMessage,
								e1);
			}
		}
	}

	protected void updateControls() {
		setMessage(null);
		setErrorMessage(null);
		if (!internalMode) {
			setDescription(UIText.ExistingOrNewPage_DescriptionExternalMode);
			if (this.selectedRepository != null) {
				workDir.setText(this.selectedRepository.getWorkTree().getPath());
				String relativePath = relPath.getText();
				File testFile = new File(this.selectedRepository.getWorkTree(),
						relativePath);
				if (!testFile.exists())
					setMessage(
							NLS.bind(
									UIText.ExistingOrNewPage_FolderWillBeCreatedMessage,
									relativePath), IMessageProvider.WARNING);
				IPath targetPath = new Path(selectedRepository.getWorkTree()
						.getPath());
				targetPath = targetPath.append(relPath.getText());
				moveProjectsLabelProvider.targetFolder = targetPath;
				projectMoveViewer.refresh(true);
				browseRepository.setEnabled(this.selectedRepository != null);
				for (Object checked : projectMoveViewer.getCheckedElements()) {
					IProject prj = (IProject) checked;
					IPath projectMoveTarget = targetPath.append(prj.getName());
					boolean mustMove = !prj.getLocation().equals(
							projectMoveTarget);
					File targetTest = new File(projectMoveTarget.toOSString());
					if (mustMove && targetTest.exists()) {
						setErrorMessage(NLS
								.bind(UIText.ExistingOrNewPage_ExistingTargetErrorMessage,
										prj.getName()));
						break;
					}
					File parent = targetTest.getParentFile();
					while (parent != null) {
						if (new File(parent, ".project").exists()) { //$NON-NLS-1$
							setErrorMessage(NLS
									.bind(UIText.ExistingOrNewPage_NestedProjectErrorMessage,
											new String[] { prj.getName(),
													targetTest.getPath(),
													parent.getPath() }));
							break;
						}
						parent = parent.getParentFile();
					}
					// break after the first error
					if (getErrorMessage() != null)
						break;
				}
			} else
				workDir.setText(UIText.ExistingOrNewPage_NoRepositorySelectedMessage);
			setPageComplete(getErrorMessage() == null
					&& selectedRepository != null
					&& projectMoveViewer.getCheckedElements().length > 0);
		} else {
			setDescription(UIText.ExistingOrNewPage_description);
			minumumPath = null;
			IPath p = null;
			for (TreeItem ti : tree.getSelection()) {
				if (ti.getItemCount() > 0)
					continue;
				String path = ti.getText(2);
				if (!path.equals("")) { //$NON-NLS-1$
					p = null;
					break;
				}
				String gitDirParentCandidate = ti.getText(1);
				IPath thisPath = Path.fromOSString(gitDirParentCandidate);
				if (p == null)
					p = thisPath;
				else {
					int n = p.matchingFirstSegments(thisPath);
					p = p.removeLastSegments(p.segmentCount() - n);
				}
			}
			minumumPath = p;
			if (p != null) {
				repositoryToCreate.setText(p.toOSString());
			} else {
				repositoryToCreate.setText(""); //$NON-NLS-1$
			}
			button.setEnabled(p != null);
			repositoryToCreate.setEnabled(p != null);
			dotGitSegment.setEnabled(p != null);

			boolean pageComplete = viewer.getCheckedElements().length > 0;
			for (Object checkedElement : viewer.getCheckedElements()) {
				String path = ((ProjectAndRepo) checkedElement).getRepo();
				if (((ProjectAndRepo) checkedElement).getRepo() != null
						&& path.equals("")) { //$NON-NLS-1$
					pageComplete = false;
				}
			}
			setPageComplete(pageComplete);
			// provide a warning if Repository is created in workspace
			for (IProject project : myWizard.projects) {
				if (button.isEnabled()
						&& ResourcesPlugin.getWorkspace().getRoot()
								.getLocation()
								.isPrefixOf(project.getLocation())) {
					setMessage(
							UIText.ExistingOrNewPage_RepoCreationInWorkspaceCreationWarning,
							IMessageProvider.WARNING);
					break;
				}
			}
		}

		externalComposite.setVisible(!internalMode);
		parentRepoComposite.setVisible(internalMode);
		GridData gd;
		gd = (GridData) parentRepoComposite.getLayoutData();
		gd.exclude = !internalMode;

		gd = (GridData) externalComposite.getLayoutData();
		gd.exclude = internalMode;

		((Composite) getControl()).layout(true);
	}

	/**
	 * @param checked
	 *            pass true to get the checked elements, false to get the
	 *            selected elements
	 * @return map between project and repository root directory (converted to
	 *         an absolute path) for all projects selected by user
	 */
	public Map<IProject, File> getProjects(boolean checked) {
		final Object[] elements;
		if (!internalMode)
			if (checked)
				elements = projectMoveViewer.getCheckedElements();
			else {
				ISelection selection = viewer.getSelection();
				elements = ((IStructuredSelection) selection).toArray();
			}
		else if (checked)
			elements = viewer.getCheckedElements();
		else {
			ISelection selection = viewer.getSelection();
			if (selection instanceof IStructuredSelection)
				elements = ((IStructuredSelection) selection).toArray();
			else
				elements = new Object[0];
		}

		Map<IProject, File> ret = new HashMap<IProject, File>(elements.length);
		for (Object ti : elements) {
			if (!internalMode) {
				File workdir = selectedRepository.getWorkTree();
				IProject project = (IProject) ti;
				IPath targetLocation = new Path(relPath.getText())
						.append(project.getName());
				File targetFile = new File(workdir, targetLocation.toOSString());
				ret.put(project, targetFile);

			} else {
				final IProject project = ((ProjectAndRepo) ti).getProject();
				String path = ((ProjectAndRepo) ti).getRepo();
				final IPath selectedRepo = Path.fromOSString(path);
				IPath localPathToRepo = selectedRepo;
				if (!selectedRepo.isAbsolute()) {
					localPathToRepo = project.getLocation()
							.append(selectedRepo);
				}
				ret.put(project, localPathToRepo.toFile());
			}
		}
		return ret;
	}

	public boolean getInternalMode() {
		return internalMode;
	}

	private static class ProjectAndRepo {
		private IProject project;

		private String repo;

		public ProjectAndRepo(IProject project, String repo) {
			this.project = project;
			this.repo = repo;
		}

		public IProject getProject() {
			return project;
		}

		public String getRepo() {
			return repo;
		}
	}

	public Repository getSelectedRepsoitory() {
		return selectedRepository;
	}
}
