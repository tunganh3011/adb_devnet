package com.example.myapplication.navdemo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.myapplication.R

class SuccessFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_success, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.btnNext).setOnClickListener {
            // Chuyển sang Màn 3
            findNavController().navigate(R.id.action_successFragment_to_infoFragment)
        }

        view.findViewById<Button>(R.id.btnBack).setOnClickListener {
            // Quay lại màn hình trước đó
            findNavController().popBackStack()
        }
    }
}
