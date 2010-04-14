import org.eclipse.egit.ui.UIText;
import org.eclipse.osgi.util.NLS;
	private static final String SPACE = " "; //$NON-NLS-1$

	private static final String LF = "\n"; //$NON-NLS-1$

	private static final String EMPTY = ""; //$NON-NLS-1$

		d.append(UIText.CommitMessageViewer_commit);
		d.append(SPACE);
		d.append(LF);
			d.append(UIText.CommitMessageViewer_author);
			d.append(": ");  //$NON-NLS-1$
			d.append(LF);
			d.append(UIText.CommitMessageViewer_committer);
			d.append(": ");  //$NON-NLS-1$
			d.append(LF);
			d.append(UIText.CommitMessageViewer_parent);
			d.append(": ");  //$NON-NLS-1$
			d.append(LF);
			d.append(UIText.CommitMessageViewer_child);
			d.append(":  ");  //$NON-NLS-1$
			d.append(LF);
		d.append(LF);
		d.append(LF);
			Activator.error(NLS.bind(UIText.CommitMessageViewer_errorGettingFileDifference,
					commit.getId()), e);
		d.append(formatPathLine(path)).append(LF);
			d.append(UIText.CommitMessageViewer_newFileMode
					+ SPACE
					+ mode2).append(LF);
			d.append(UIText.CommitMessageViewer_deletedFileMode + SPACE + mode1).append(LF);
			d.append(UIText.CommitMessageViewer_oldMode + SPACE + mode1);
			d.append(UIText.CommitMessageViewer_newMode + SPACE + mode2).append(LF);
		d.append(UIText.CommitMessageViewer_index).append(SPACE).append(id1.abbreviate(db, 7).name()).
			append (mode1.equals(mode2) ? SPACE + mode1 : EMPTY). append(LF);
		d.append(LF);
		d.append(SPACE).append(path).append(SPACE);