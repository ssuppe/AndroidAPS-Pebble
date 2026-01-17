package app.aaps.plugins.pebble

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.pebble.databinding.PebbleFragmentBinding
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class PebbleFragment : DaggerFragment() {

    @Inject lateinit var uuidProvider: TargetUuidProvider
    @Inject lateinit var rh: ResourceHelper

    private var _binding: PebbleFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PebbleFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.pebbleUuid.setText(uuidProvider.getTargetUuid().toString())
        
        binding.saveButton.setOnClickListener {
            val uuidString = binding.pebbleUuid.text.toString()
            try {
                java.util.UUID.fromString(uuidString)
                uuidProvider.saveTargetUuid(uuidString)
                ToastUtils.okToast(activity, rh.gs(R.string.pebble_saved))
            } catch (e: IllegalArgumentException) {
                ToastUtils.errorToast(activity, rh.gs(R.string.pebble_invalid_uuid))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
