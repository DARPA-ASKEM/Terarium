import { Datacube } from '../types/Datacube';
import { XDDArticle, XDDResult } from '../types/XDD';

const fetchData = async (xdd = false) => {
	// @TEMP: mock data simulating fetching of data resources from the server
	const tempDataList: (Datacube | XDDArticle)[] = [];

	if (xdd) {
		const url =
			'https://xdd.wisc.edu/api/articles?dataset=xdd-covid-19&term=github&dict=genes%2Ccovid-19_drugs&max=100&include_score=true';
		const response = await fetch(url);
		const rawdata: XDDResult = await response.json();
		const { data } = rawdata.success;
		tempDataList.push(...data);
	} else {
		//
		// mock data
		//
		tempDataList.push({
			id: '1',
			description: 'test cube 1',
			status: 'ready',
			outputs: [],
			display_name: 'one',
			name: 'one',
			type: 'model'
		});

		tempDataList.push({
			id: '2',
			description: 'another lone desc for cube 2',
			status: 'deprecated',
			outputs: [],
			display_name: 'two',
			name: 'two_',
			type: 'model'
		});

		tempDataList.push({
			id: '3',
			description: 'happy face',
			status: 'ready',
			outputs: [],
			display_name: 'three',
			name: 'three',
			type: 'model'
		});

		tempDataList.push({
			id: '4',
			description: '',
			status: 'disabled',
			outputs: [],
			display_name: 'four',
			name: 'four4',
			type: 'dataset'
		});

		tempDataList.push({
			id: '5',
			description: 'i like this one',
			status: 'processing',
			outputs: [],
			display_name: 'Five',
			name: 'five',
			type: 'model'
		});

		tempDataList.push({
			id: '6',
			description: 'yes yes',
			status: 'ready',
			outputs: [],
			display_name: 'six',
			name: 'six',
			type: 'model'
		});

		tempDataList.push({
			id: '7',
			description: 'nice work',
			status: 'ready',
			outputs: [],
			display_name: 'seven',
			name: 'seven',
			type: 'dataset'
		});

		tempDataList.push({
			id: '8',
			description: '',
			status: 'disabled',
			outputs: [],
			display_name: 'eight',
			name: 'eight',
			type: 'model'
		});

		tempDataList.push({
			id: '9',
			description: 'this is a copy of the data explorer showing a lot of nice things',
			status: 'ready',
			outputs: [],
			display_name: 'nine',
			name: 'nine',
			type: 'model'
		});

		tempDataList.push({
			id: '10',
			description: 'good work',
			status: 'registered',
			outputs: [],
			display_name: 'ten',
			name: 'ten',
			type: 'model'
		});
	}

	return tempDataList;
};

export default fetchData;
