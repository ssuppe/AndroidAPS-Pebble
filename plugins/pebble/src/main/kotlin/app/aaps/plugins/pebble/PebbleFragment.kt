package app.aaps.plugins.pebble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.pebble.databinding.PebbleFragmentBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class PebbleFragment : DaggerFragment() {

    @Inject lateinit var uuidProvider: TargetUuidProvider
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    private var _binding: PebbleFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        aapsLogger.debug(LTag.PEBBLE, "PebbleFragment: onCreateView")
        _binding = PebbleFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val currentUuid = uuidProvider.getTargetUuid().toString()
        aapsLogger.debug(LTag.PEBBLE, "PebbleFragment: onViewCreated, current UUID: {}", currentUuid)
        binding.pebbleUuid.setText(currentUuid)
        
        binding.saveButton.setOnClickListener {
            val uuidString = binding.pebbleUuid.text.toString()
            aapsLogger.debug(LTag.PEBBLE, "PebbleFragment: Save button clicked with UUID string: {}", uuidString)
            try {
                java.util.UUID.fromString(uuidString)
                uuidProvider.saveTargetUuid(uuidString)
                aapsLogger.debug(LTag.PEBBLE, "PebbleFragment: UUID saved successfully")
                ToastUtils.okToast(activity, rh.gs(R.string.pebble_saved))
            } catch (e: IllegalArgumentException) {
                aapsLogger.warn(LTag.PEBBLE, "PebbleFragment: Invalid UUID format entered: {}", uuidString)
                ToastUtils.errorToast(activity, rh.gs(R.string.pebble_invalid_uuid))
            }
        }
    }

    override fun onDestroyView() {
        aapsLogger.debug(LTag.PEBBLE, "PebbleFragment: onDestroyView")
        super.onDestroyView()
        _binding = null
    }
}
