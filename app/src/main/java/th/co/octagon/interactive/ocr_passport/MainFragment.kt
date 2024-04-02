package th.co.octagon.interactive.ocr_passport

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.google.gson.Gson
import th.co.octagon.interactive.ocr_passport.databinding.FragmentMainBinding
import th.co.octagon.interactive.ocr_passport.databinding.FragmentOcrBinding
import th.co.octagon.interactive.ocr_passport.model.PassportModel

class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initView()
        val exampleData = arguments?.getString("exampleData")
        exampleData?.let {

            val gson = Gson()
            val person = gson.fromJson(exampleData, PassportModel::class.java)

            println(exampleData)
            binding.edtDocument.text = person.documentNumber
            binding.edtBirthDay.text = person.birthDate.toString()
            binding.edtExpiryDay.text = person.expiryDate.toString()
        }

        return root
    }

    private fun initView() {
        binding.btCamera.setOnClickListener {
            val action = MainFragmentDirections.actionMainFragmentToOcrFragment()
            findNavController().navigate(action)
        }
    }
}