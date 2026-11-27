import AVFoundation
import Foundation

/**
 الكاميرا الخلفية والفلاش للقياس فقط: تُفتح عند البدء وتُغلق مع الفلاش عند الانتهاء.
 لكل قياس كائن جديد. لا تُحفظ الصور ولا تغادر الجوال: يُحسب من كل إطار متوسط الألوان فقط.
 الدوال المرسلة (onFrame وغيرها) تُستدعى على الخيط الرئيسي.
 */
final class PulseCamera: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    private let session = AVCaptureSession()
    private let queue = DispatchQueue(label: "ai.alshaye.afiati.pulse")
    private var device: AVCaptureDevice?
    private var stopped = false
    private var firstTime = -1.0

    var onFrame: ((FrameStats) -> Void)?
    var onReady: ((Bool) -> Void)?
    var onError: ((String) -> Void)?

    static var authorized: Bool { AVCaptureDevice.authorizationStatus(for: .video) == .authorized }
    static var undetermined: Bool { AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined }

    static func requestAccess() async -> Bool { await AVCaptureDevice.requestAccess(for: .video) }

    func start() {
        queue.async { [weak self] in self?.configure() }
    }

    private func fail() {
        let message = tr("تعذّر تشغيل الكاميرا. أغلق التطبيقات التي تستخدمها وحاول مرة أخرى.", "Couldn't start the camera. Close apps that use it and try again.")
        DispatchQueue.main.async { [weak self] in self?.onError?(message) }
        stop()
    }

    private func configure() {
        if stopped { return }
        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let input = try? AVCaptureDeviceInput(device: camera) else { return fail() }
        device = camera
        session.beginConfiguration()
        // صورة صغيرة تكفي: نحتاج متوسط اللون فقط
        if session.canSetSessionPreset(.low) { session.sessionPreset = .low }
        guard session.canAddInput(input) else { session.commitConfiguration(); return fail() }
        session.addInput(input)
        let output = AVCaptureVideoDataOutput()
        output.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
        output.alwaysDiscardsLateVideoFrames = true
        output.setSampleBufferDelegate(self, queue: queue)
        guard session.canAddOutput(output) else { session.commitConfiguration(); return fail() }
        session.addOutput(output)
        session.commitConfiguration()

        // 30 إطارًا في الثانية إن دعمها التنسيق الحالي
        if (try? camera.lockForConfiguration()) != nil {
            let supports30 = camera.activeFormat.videoSupportedFrameRateRanges.contains { $0.minFrameRate <= 30 && $0.maxFrameRate >= 30 }
            if supports30 {
                camera.activeVideoMinFrameDuration = CMTime(value: 1, timescale: 30)
                camera.activeVideoMaxFrameDuration = CMTime(value: 1, timescale: 30)
            }
            camera.unlockForConfiguration()
        }
        session.startRunning()
        if stopped { session.stopRunning(); return }

        let hasTorch = camera.hasTorch && camera.isTorchModeSupported(.on)
        if hasTorch, (try? camera.lockForConfiguration()) != nil {
            try? camera.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
            camera.unlockForConfiguration()
        }
        DispatchQueue.main.async { [weak self] in
            guard let self = self, !self.stopped else { return }
            self.onReady?(hasTorch)
        }
    }

    /// يطفئ الفلاش ويغلق الكاميرا. آمن للاستدعاء أكثر من مرة ومن أي خيط.
    func stop() {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.stopped = true
            if let camera = self.device, camera.hasTorch, (try? camera.lockForConfiguration()) != nil {
                camera.torchMode = .off
                camera.unlockForConfiguration()
            }
            if self.session.isRunning { self.session.stopRunning() }
        }
        stopped = true
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        if stopped { return }
        guard let buffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let time = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer))
        if firstTime < 0 { firstTime = time }
        guard let stats = average(buffer, seconds: time - firstTime) else { return }
        DispatchQueue.main.async { [weak self] in
            guard let self = self, !self.stopped else { return }
            self.onFrame?(stats)
        }
    }

    /// متوسط Y وCb وCr في نصف الصورة الأوسط، بخطوة 4 بكسلات
    private func average(_ buffer: CVPixelBuffer, seconds: Double) -> FrameStats? {
        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(buffer, .readOnly) }
        guard CVPixelBufferGetPlaneCount(buffer) >= 2,
              let yBase = CVPixelBufferGetBaseAddressOfPlane(buffer, 0)?.assumingMemoryBound(to: UInt8.self),
              let cBase = CVPixelBufferGetBaseAddressOfPlane(buffer, 1)?.assumingMemoryBound(to: UInt8.self) else { return nil }
        let width = CVPixelBufferGetWidthOfPlane(buffer, 0)
        let height = CVPixelBufferGetHeightOfPlane(buffer, 0)
        let yStride = CVPixelBufferGetBytesPerRowOfPlane(buffer, 0)
        let cStride = CVPixelBufferGetBytesPerRowOfPlane(buffer, 1)
        var sumY = 0, sumCb = 0, sumCr = 0, count = 0
        var row = height / 4
        while row < height * 3 / 4 {
            var column = width / 4
            while column < width * 3 / 4 {
                sumY += Int(yBase[row * yStride + column])
                let chroma = (row / 2) * cStride + (column / 2) * 2
                sumCb += Int(cBase[chroma])
                sumCr += Int(cBase[chroma + 1])
                count += 1
                column += 4
            }
            row += 4
        }
        guard count > 0 else { return nil }
        let n = Double(count)
        return FrameStats(timeSeconds: seconds, luma: Double(sumY) / n, cb: Double(sumCb) / n, cr: Double(sumCr) / n)
    }
}
