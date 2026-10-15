import 'package:flutter/cupertino.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../bridge/reader_api.g.dart';
import '../../bridge/reader_repository.dart';
import '../../shared/format_bytes.dart';
import '../../shared/readable_width.dart';
import '../../shared/widgets/empty_state.dart';
import '../../shared/widgets/thin_progress_bar.dart';
import 'copy_jobs.dart';

class CopyScreen extends ConsumerWidget {
  const CopyScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final jobs = ref.watch(copyJobsProvider).values.toList()..sort((a, b) => b.jobId.compareTo(a.jobId));
    final running = jobs.where((job) => activeCopyStates.contains(job.state)).toList();
    final finished = jobs.where((job) => !activeCopyStates.contains(job.state)).toList();
    return CupertinoPageScaffold(
      backgroundColor: CupertinoColors.systemGroupedBackground,
      child: CustomScrollView(
        slivers: [
          const CupertinoSliverNavigationBar(largeTitle: Text('Penyalinan')),
          if (jobs.isEmpty)
            const SliverFillRemaining(
              hasScrollBody: false,
              child: EmptyState(
                icon: CupertinoIcons.arrow_down_doc,
                title: 'Belum ada penyalinan',
                subtitle: 'File yang kamu salin ke HP akan muncul di sini.',
              ),
            ),
          if (running.isNotEmpty)
            SliverPadding(
              padding: readableInsets(context),
              sliver: SliverToBoxAdapter(
                child: CupertinoListSection.insetGrouped(
                  header: const Text('Sedang berjalan'),
                  children: [for (final job in running) _RunningJobTile(job: job)],
                ),
              ),
            ),
          if (finished.isNotEmpty)
            SliverPadding(
              padding: readableInsets(context),
              sliver: SliverToBoxAdapter(
                child: CupertinoListSection.insetGrouped(
                  header: const Text('Selesai'),
                  children: [for (final job in finished) _FinishedJobTile(job: job)],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _RunningJobTile extends ConsumerWidget {
  const _RunningJobTile({required this.job});

  final CopyProgressMessage job;

  Future<void> _confirmCancel(BuildContext context, WidgetRef ref) async {
    final confirmed = await showCupertinoDialog<bool>(
      context: context,
      builder: (dialogContext) => CupertinoAlertDialog(
        title: const Text('Batalkan penyalinan?'),
        content: const Text('File yang belum selesai akan dihapus dari folder tujuan.'),
        actions: [
          CupertinoDialogAction(onPressed: () => Navigator.pop(dialogContext, false), child: const Text('Lanjutkan')),
          CupertinoDialogAction(
            isDestructiveAction: true,
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Batalkan'),
          ),
        ],
      ),
    );
    if (confirmed == true) await ref.read(readerRepositoryProvider).cancelCopy(job.jobId);
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final secondary = CupertinoColors.secondaryLabel.resolveFrom(context);
    final fraction = job.totalBytes > 0 ? job.copiedBytes / job.totalBytes : 0.0;
    final isQueued = job.state == 'queued';
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 8, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  isQueued ? 'Menunggu giliran…' : (job.currentName.isEmpty ? 'Menyiapkan…' : job.currentName),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
              CupertinoButton(
                padding: const EdgeInsets.symmetric(horizontal: 8),
                minimumSize: const Size(28, 28),
                onPressed: () => _confirmCancel(context, ref),
                child: Text('Batal', style: TextStyle(color: CupertinoColors.systemRed.resolveFrom(context))),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ThinProgressBar(fraction: fraction),
          ),
          const SizedBox(height: 6),
          Text(
            '${job.copiedFiles} dari ${job.totalFiles} file · ${formatBytes(job.copiedBytes)} dari ${formatBytes(job.totalBytes)} · ${formatBytes(job.bytesPerSecond)}/s',
            style: TextStyle(fontSize: 13, color: secondary),
          ),
          Text('Ke ${job.destinationName}', style: TextStyle(fontSize: 13, color: secondary)),
        ],
      ),
    );
  }
}

class _FinishedJobTile extends StatelessWidget {
  const _FinishedJobTile({required this.job});

  final CopyProgressMessage job;

  @override
  Widget build(BuildContext context) {
    final (icon, color, label) = switch (job.state) {
      'completed' => (CupertinoIcons.checkmark_circle_fill, CupertinoColors.systemGreen, 'Selesai'),
      'cancelled' => (CupertinoIcons.xmark_circle_fill, CupertinoColors.systemGrey, 'Dibatalkan'),
      _ => (CupertinoIcons.exclamationmark_circle_fill, CupertinoColors.systemRed, 'Gagal'),
    };
    final details = [label, 'ke ${job.destinationName}', if (job.errorMessage != null) job.errorMessage!];
    return CupertinoListTile(
      leading: Icon(icon, color: color.resolveFrom(context)),
      title: Text('${job.copiedFiles} file · ${formatBytes(job.copiedBytes)}'),
      subtitle: Text(details.join(' · '), maxLines: 3),
    );
  }
}
